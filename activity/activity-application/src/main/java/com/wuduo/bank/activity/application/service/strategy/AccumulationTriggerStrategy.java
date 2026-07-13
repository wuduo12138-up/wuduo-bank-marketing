package com.wuduo.bank.activity.application.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuduo.bank.activity.api.dto.EventReportRequest;
import com.wuduo.bank.activity.api.dto.RuleConfig;
import com.wuduo.bank.activity.api.enums.FrequencyPeriod;
import com.wuduo.bank.activity.api.enums.TriggerType;
import com.wuduo.bank.activity.domain.entity.Activity;
import com.wuduo.bank.activity.domain.entity.ActivityParticipation;
import com.wuduo.bank.activity.domain.entity.ActivityUserProgress;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityParticipationMapper;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityUserProgressMapper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.point.api.client.PointFeignClient;
import com.wuduo.bank.point.api.dto.PointIssueRequest;
import com.wuduo.bank.point.api.enums.PointRecordType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Strategy for {@link TriggerType#ACCUMULATION_TRIGGER} activities.
 * Each event increments a counter. Completions are awarded when the counter
 * crosses threshold boundaries, capped by a per-period frequency limit.
 * <p>
 * Key: the counter does NOT reset after a completion.
 * {@code floor(currentValue / threshold)} determines total earned completions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccumulationTriggerStrategy implements ActivityTriggerStrategy {

    private static final int MAX_RETRIES = 3;

    private final ActivityParticipationMapper participationMapper;
    private final ActivityUserProgressMapper progressMapper;
    private final PointFeignClient pointFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public TriggerType supportedType() {
        return TriggerType.ACCUMULATION_TRIGGER;
    }

    @Override
    public int processEvent(EventReportRequest request, Activity activity, RuleConfig ruleConfig) {
        // 1. Idempotency check
        Long existingCount = participationMapper.selectCount(
                new LambdaQueryWrapper<ActivityParticipation>()
                        .eq(ActivityParticipation::getActivityId, activity.getId())
                        .eq(ActivityParticipation::getUserId, request.getUserId())
                        .eq(ActivityParticipation::getBizNo, request.getBizNo()));
        if (existingCount > 0) {
            log.info("Duplicate ACCUMULATION_TRIGGER event ignored: activityId={}, userId={}, bizNo={}",
                    activity.getId(), request.getUserId(), request.getBizNo());
            return 0;
        }

        // 2. Determine period key
        FrequencyPeriod period = ruleConfig.getFrequencyPeriodEnum();
        if (period == null) {
            period = FrequencyPeriod.LIFETIME;
        }
        String periodKey = period.generatePeriodKey();

        // 3. Get or create progress
        ActivityUserProgress progress = getOrCreateProgress(activity.getId(), request.getUserId(), periodKey);

        // 4. Increment current value by the configured accumulate field (or +1 per event)
        long increment = extractAccumulateValue(ruleConfig, request);
        updateProgressWithRetry(progress, p -> {
            p.setCurrentValue(p.getCurrentValue() + increment);
        });

        // 5. Calculate completions earned
        long threshold = ruleConfig.getThreshold() != null ? ruleConfig.getThreshold() : 1;
        int completionsEarned = (int) (progress.getCurrentValue() / threshold);

        // 6. Cap by frequency limit
        int maxCount = ruleConfig.getFrequency() != null && ruleConfig.getFrequency().getMaxCount() != null
                ? ruleConfig.getFrequency().getMaxCount() : Integer.MAX_VALUE;
        int capped = Math.min(completionsEarned, maxCount);

        // 7. How many NEW completions to award?
        int newCompletions = capped - progress.getCompletionCount();
        if (newCompletions <= 0) {
            log.info("ACCUMULATION_TRIGGER no new completions: activityId={}, userId={}, currentValue={}, threshold={}, capped={}, completionCount={}",
                    activity.getId(), request.getUserId(), progress.getCurrentValue(), threshold, capped, progress.getCompletionCount());
            return 0;
        }

        // 8. Issue rewards for each new completion
        long rewardAmount = ruleConfig.getReward() != null && ruleConfig.getReward().getAmount() != null
                ? ruleConfig.getReward().getAmount() : 0L;
        for (int i = 0; i < newCompletions; i++) {
            String rewardBizNo = request.getBizNo() + "_C" + (i + 1);
            if (rewardAmount > 0) {
                issuePointsReward(request.getUserId(), rewardAmount, rewardBizNo, activity.getId());
            }
            createParticipation(activity.getId(), request.getUserId(), rewardBizNo,
                    periodKey, rewardAmount, TriggerType.ACCUMULATION_TRIGGER.getCode());
        }

        // 9. Update completion count to match new capped value
        final int finalCapped = capped;
        updateProgressWithRetry(progress, p -> {
            p.setCompletionCount(finalCapped);
        });

        log.info("ACCUMULATION_TRIGGER awarded {} completions: activityId={}, userId={}, currentValue={}, completionCount={}",
                newCompletions, activity.getId(), request.getUserId(), progress.getCurrentValue(), capped);
        return newCompletions;
    }

    // -------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------

    /**
     * Extract the value to add to the accumulator from the current event.
     * <p>
     * When {@link RuleConfig#getAccumulateField()} is configured, this method
     * reads the named field from {@code eventData} JSON and returns its numeric
     * value. Otherwise it returns 1 (event-counting mode — each event increments
     * the counter by one).
     *
     * @param ruleConfig parsed activity rule configuration
     * @param request    the incoming event report
     * @return the increment value (≥ 0)
     */
    private long extractAccumulateValue(RuleConfig ruleConfig, EventReportRequest request) {
        String accumulateField = ruleConfig.getAccumulateField();
        if (accumulateField == null || accumulateField.isBlank()) {
            // Default mode: count events (each event = 1)
            return 1L;
        }

        String eventData = request.getEventData();
        if (eventData == null || eventData.isBlank()) {
            throw new BizException(ErrorCode.ACTIVITY_EVENT_DATA_PARSE_ERROR,
                    "eventData is required when accumulateField=" + accumulateField);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.readValue(eventData, Map.class);
            Object fieldValue = dataMap.get(accumulateField);
            if (fieldValue == null) {
                throw new BizException(ErrorCode.ACTIVITY_EVENT_DATA_PARSE_ERROR,
                        "Field '" + accumulateField + "' not found in eventData: " + eventData);
            }

            // Parse the value as a long (e.g. "100.00" → 100)
            if (fieldValue instanceof Number) {
                return ((Number) fieldValue).longValue();
            }
            return new BigDecimal(fieldValue.toString()).longValue();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse accumulateField={} from eventData={}",
                    accumulateField, eventData, e);
            throw new BizException(ErrorCode.ACTIVITY_EVENT_DATA_PARSE_ERROR,
                    "Failed to parse field '" + accumulateField + "': " + e.getMessage());
        }
    }

    private ActivityUserProgress getOrCreateProgress(Long activityId, Long userId, String periodKey) {
        ActivityUserProgress progress = progressMapper.selectOne(
                new LambdaQueryWrapper<ActivityUserProgress>()
                        .eq(ActivityUserProgress::getActivityId, activityId)
                        .eq(ActivityUserProgress::getUserId, userId)
                        .eq(ActivityUserProgress::getPeriodKey, periodKey));
        if (progress == null) {
            progress = new ActivityUserProgress();
            progress.setActivityId(activityId);
            progress.setUserId(userId);
            progress.setPeriodKey(periodKey);
            progress.setCurrentValue(0L);
            progress.setCompletionCount(0);
            progress.setVersion(0);
            progressMapper.insert(progress);
        }
        return progress;
    }

    private void updateProgressWithRetry(ActivityUserProgress progress, Consumer<ActivityUserProgress> applyDelta) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            Integer currentVersion = progress.getVersion();
            applyDelta.accept(progress);

            LambdaUpdateWrapper<ActivityUserProgress> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ActivityUserProgress::getId, progress.getId())
                    .eq(ActivityUserProgress::getVersion, currentVersion)
                    .set(ActivityUserProgress::getVersion, currentVersion + 1)
                    .set(ActivityUserProgress::getCurrentValue, progress.getCurrentValue())
                    .set(ActivityUserProgress::getCompletionCount, progress.getCompletionCount());

            int updated = progressMapper.update(null, wrapper);
            if (updated > 0) {
                progress.setVersion(currentVersion + 1);
                return;
            }

            log.warn("Optimistic lock conflict for progress id={}, retry {}/{}",
                    progress.getId(), i + 1, MAX_RETRIES);

            ActivityUserProgress latest = progressMapper.selectById(progress.getId());
            if (latest == null) {
                throw new BizException(ErrorCode.ACTIVITY_PROGRESS_NOT_FOUND);
            }
            progress = latest;

            if (i < MAX_RETRIES - 1) {
                try {
                    Thread.sleep((long) (Math.random() * 50 * (i + 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException(ErrorCode.INTERNAL_ERROR);
                }
            }
        }
        throw new BizException(ErrorCode.POINT_VERSION_CONFLICT);
    }

    private void issuePointsReward(Long userId, Long amount, String bizNo, Long activityId) {
        try {
            PointIssueRequest issueRequest = new PointIssueRequest();
            issueRequest.setUserId(userId);
            issueRequest.setPointAmount(amount);
            issueRequest.setType(PointRecordType.ACTIVITY_EARN.getCode());
            issueRequest.setBizSource("ACTIVITY_" + activityId);
            issueRequest.setBizNo(bizNo);
            pointFeignClient.issue(issueRequest);
            log.info("Issued {} points to userId={} for activityId={}, bizNo={}",
                    amount, userId, activityId, bizNo);
        } catch (Exception e) {
            log.error("Failed to issue points: userId={}, amount={}, bizNo={}", userId, amount, bizNo, e);
            throw new BizException(ErrorCode.ACTIVITY_REWARD_FAILED);
        }
    }

    private void createParticipation(Long activityId, Long userId, String bizNo,
                                     String periodKey, Long rewardAmount, int triggerTypeCode) {
        ActivityParticipation participation = new ActivityParticipation();
        participation.setActivityId(activityId);
        participation.setUserId(userId);
        participation.setParticipationType(triggerTypeCode);
        participation.setRewardAmount(BigDecimal.valueOf(rewardAmount));
        participation.setStatus(1); // completed
        participation.setBizNo(bizNo);
        participation.setPeriodKey(periodKey);
        participationMapper.insert(participation);
    }
}
