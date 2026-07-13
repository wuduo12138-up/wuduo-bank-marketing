package com.wuduo.bank.activity.application.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.function.Consumer;

/**
 * Strategy for {@link TriggerType#SINGLE_TRIGGER} activities.
 * One business event → one activity completion, subject to a per-period frequency cap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleTriggerStrategy implements ActivityTriggerStrategy {

    private static final int MAX_RETRIES = 3;

    private final ActivityParticipationMapper participationMapper;
    private final ActivityUserProgressMapper progressMapper;
    private final PointFeignClient pointFeignClient;

    @Override
    public TriggerType supportedType() {
        return TriggerType.SINGLE_TRIGGER;
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
            log.info("Duplicate SINGLE_TRIGGER event ignored: activityId={}, userId={}, bizNo={}",
                    activity.getId(), request.getUserId(), request.getBizNo());
            return 0;
        }

        // 2. Determine period key
        FrequencyPeriod period = ruleConfig.getFrequencyPeriodEnum();
        if (period == null) {
            period = FrequencyPeriod.LIFETIME;
        }
        String periodKey = period.generatePeriodKey();

        // 3. Get or create progress for this period
        ActivityUserProgress progress = getOrCreateProgress(activity.getId(), request.getUserId(), periodKey);

        // 4. Check frequency cap
        int maxCount = ruleConfig.getFrequency() != null && ruleConfig.getFrequency().getMaxCount() != null
                ? ruleConfig.getFrequency().getMaxCount() : 1;
        if (progress.getCompletionCount() >= maxCount) {
            throw new BizException(ErrorCode.ACTIVITY_FREQUENCY_EXCEEDED);
        }

        // 5. Issue reward
        long rewardAmount = ruleConfig.getReward() != null && ruleConfig.getReward().getAmount() != null
                ? ruleConfig.getReward().getAmount() : 0L;
        if (rewardAmount > 0) {
            issuePointsReward(request.getUserId(), rewardAmount, request.getBizNo(), activity.getId());
        }

        // 6. Create participation record
        createParticipation(activity.getId(), request.getUserId(), request.getBizNo(),
                periodKey, rewardAmount, TriggerType.SINGLE_TRIGGER.getCode());

        // 7. Increment completion count (optimistic lock)
        updateProgressWithRetry(progress, p -> {
            p.setCompletionCount(p.getCompletionCount() + 1);
        });

        log.info("SINGLE_TRIGGER completed: activityId={}, userId={}, periodKey={}, completionCount={}",
                activity.getId(), request.getUserId(), periodKey, progress.getCompletionCount());
        return 1;
    }

    // -------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------

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
