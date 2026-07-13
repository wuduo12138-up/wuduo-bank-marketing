package com.wuduo.bank.activity.boot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuduo.bank.activity.api.dto.RuleConfig;
import com.wuduo.bank.activity.api.enums.ActivityStatus;
import com.wuduo.bank.activity.api.enums.ActivityType;
import com.wuduo.bank.activity.api.enums.FrequencyPeriod;
import com.wuduo.bank.activity.api.enums.TriggerType;
import com.wuduo.bank.activity.domain.entity.Activity;
import com.wuduo.bank.activity.domain.entity.ActivityParticipation;
import com.wuduo.bank.activity.domain.entity.ActivityUserProgress;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityMapper;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityParticipationMapper;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityUserProgressMapper;
import com.wuduo.bank.activity.infrastructure.util.DistributedLockHelper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.point.api.client.PointFeignClient;
import com.wuduo.bank.point.api.dto.PointIssueRequest;
import com.wuduo.bank.point.api.enums.PointRecordType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Monthly criteria evaluation task.
 * Runs at 1:00 AM on the 1st of each month to check whether users meet
 * the criteria for {@link TriggerType#MONTHLY_CRITERIA} activities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyCriteriaTask {

    private final ActivityMapper activityMapper;
    private final ActivityUserProgressMapper progressMapper;
    private final ActivityParticipationMapper participationMapper;
    private final PointFeignClient pointFeignClient;
    private final DistributedLockHelper lockHelper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST_TYPE =
            new ParameterizedTypeReference<List<Map<String, Object>>>() {};

    /**
     * Evaluate monthly criteria activities at 1:00 AM on the 1st of each month.
     */
    @Scheduled(cron = "0 0 1 1 * ?")
    public void evaluateMonthlyCriteria() {
        log.info("Starting monthly criteria evaluation task...");
        String periodKey = FrequencyPeriod.MONTHLY.generatePeriodKey();

        // 1. Query all ONGOING MONTHLY_CRITERIA activities
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONGOING.getCode())
                        .eq(Activity::getType, ActivityType.MONTHLY_CRITERIA.getCode()));

        if (activities.isEmpty()) {
            log.info("No MONTHLY_CRITERIA activities to evaluate.");
            return;
        }

        for (Activity activity : activities) {
            RuleConfig config = parseRuleConfig(activity.getRuleConfig());
            if (config == null || config.getTriggerTypeEnum() != TriggerType.MONTHLY_CRITERIA) {
                continue;
            }
            try {
                processMonthlyCriteriaActivity(activity, config, periodKey);
            } catch (Exception e) {
                log.error("Failed to process monthly criteria for activityId={}", activity.getId(), e);
            }
        }
        log.info("Monthly criteria evaluation task completed.");
    }

    private void processMonthlyCriteriaActivity(Activity activity, RuleConfig config, String periodKey) {
        if (config.getCriteriaService() == null || config.getCriteriaEndpoint() == null) {
            log.warn("Missing criteriaService/criteriaEndpoint for activityId={}", activity.getId());
            return;
        }

        // 2. Call external service to get qualified user list
        String url = "http://" + config.getCriteriaService() + config.getCriteriaEndpoint()
                + "?activityId=" + activity.getId() + "&periodKey=" + periodKey;
        log.info("Calling criteria service: {}", url);

        List<Map<String, Object>> qualifiedUsers;
        try {
            ResponseEntity<List<Map<String, Object>>> response =
                    restTemplate.exchange(url, HttpMethod.GET, null, USER_LIST_TYPE);
            qualifiedUsers = response.getBody();
            if (qualifiedUsers == null) {
                qualifiedUsers = Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to call criteria service for activityId={}: {}", activity.getId(), e.getMessage());
            throw new BizException(ErrorCode.ACTIVITY_CRITERIA_SERVICE_ERROR);
        }

        log.info("Activity {} has {} qualified users", activity.getId(), qualifiedUsers.size());

        // 3. Process each qualified user
        long rewardAmount = config.getReward() != null && config.getReward().getAmount() != null
                ? config.getReward().getAmount() : 0L;

        for (Map<String, Object> userEntry : qualifiedUsers) {
            Long userId = toLong(userEntry.get("userId"));
            if (userId == null) {
                log.warn("Skipping entry without userId: {}", userEntry);
                continue;
            }

            try {
                lockHelper.executeWithLock(activity.getId(), userId, () -> {
                    processUserMonthlyCriteria(activity, userId, periodKey, rewardAmount);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to process userId={} for activityId={}: {}", userId, activity.getId(), e.getMessage());
            }
        }
    }

    private void processUserMonthlyCriteria(Activity activity, Long userId, String periodKey, long rewardAmount) {
        // Check if already completed this period
        ActivityUserProgress progress = progressMapper.selectOne(
                new LambdaQueryWrapper<ActivityUserProgress>()
                        .eq(ActivityUserProgress::getActivityId, activity.getId())
                        .eq(ActivityUserProgress::getUserId, userId)
                        .eq(ActivityUserProgress::getPeriodKey, periodKey));

        if (progress != null && progress.getCompletionCount() > 0) {
            log.info("User {} already completed activity {} for period {}, skipping",
                    userId, activity.getId(), periodKey);
            return;
        }

        String bizNo = "MONTHLY_" + activity.getId() + "_" + userId + "_" + periodKey;

        // Issue reward
        if (rewardAmount > 0) {
            try {
                PointIssueRequest issueRequest = new PointIssueRequest();
                issueRequest.setUserId(userId);
                issueRequest.setPointAmount(rewardAmount);
                issueRequest.setType(PointRecordType.ACTIVITY_EARN.getCode());
                issueRequest.setBizSource("ACTIVITY_" + activity.getId());
                issueRequest.setBizNo(bizNo);
                pointFeignClient.issue(issueRequest);
                log.info("Monthly criteria: issued {} points to userId={} for activityId={}",
                        rewardAmount, userId, activity.getId());
            } catch (Exception e) {
                log.error("Failed to issue monthly criteria reward: userId={}, activityId={}", userId, activity.getId(), e);
                throw new BizException(ErrorCode.ACTIVITY_REWARD_FAILED);
            }
        }

        // Create participation record
        ActivityParticipation participation = new ActivityParticipation();
        participation.setActivityId(activity.getId());
        participation.setUserId(userId);
        participation.setParticipationType(TriggerType.MONTHLY_CRITERIA.getCode());
        participation.setRewardAmount(BigDecimal.valueOf(rewardAmount));
        participation.setStatus(1);
        participation.setBizNo(bizNo);
        participation.setPeriodKey(periodKey);
        participationMapper.insert(participation);

        // Upsert progress
        if (progress == null) {
            progress = new ActivityUserProgress();
            progress.setActivityId(activity.getId());
            progress.setUserId(userId);
            progress.setPeriodKey(periodKey);
            progress.setCurrentValue(0L);
            progress.setCompletionCount(1);
            progress.setVersion(0);
            progressMapper.insert(progress);
        } else {
            progress.setCompletionCount(1);
            progressMapper.updateById(progress);
        }
    }

    private RuleConfig parseRuleConfig(String ruleConfigJson) {
        if (ruleConfigJson == null || ruleConfigJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(ruleConfigJson, RuleConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse ruleConfig: {}", ruleConfigJson, e);
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
