package com.wuduo.bank.activity.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuduo.bank.activity.api.dto.EventReportRequest;
import com.wuduo.bank.activity.api.dto.EventReportResponse;
import com.wuduo.bank.activity.api.dto.RuleConfig;
import com.wuduo.bank.activity.api.enums.ActivityStatus;
import com.wuduo.bank.activity.api.enums.ActivityType;
import com.wuduo.bank.activity.api.enums.TriggerType;
import com.wuduo.bank.activity.application.service.EventService;
import com.wuduo.bank.activity.application.service.strategy.ActivityTriggerContext;
import com.wuduo.bank.activity.application.service.strategy.ActivityTriggerStrategy;
import com.wuduo.bank.activity.domain.entity.Activity;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityMapper;
import com.wuduo.bank.activity.infrastructure.util.DistributedLockHelper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Event processing service implementation.
 * Orchestrates: find matching activities → acquire lock → dispatch to strategy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final ActivityMapper activityMapper;
    private final ActivityTriggerContext triggerContext;
    private final DistributedLockHelper lockHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventReportResponse processEvent(EventReportRequest request) {
        // 1. Validate
        if (request.getEventType() == null || request.getEventType().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 2. Query ONGOING activities of event-driven types
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONGOING.getCode())
                        .in(Activity::getType, List.of(
                                ActivityType.EVENT_DRIVEN.getCode(),
                                ActivityType.MONTHLY_CRITERIA.getCode())));

        if (activities.isEmpty()) {
            return skippedResponse("No ongoing activities found");
        }

        // 3. Match activities by eventType in ruleConfig
        int totalAwarded = 0;
        long totalReward = 0L;
        Long matchedActivityId = null;

        for (Activity activity : activities) {
            RuleConfig config = parseRuleConfig(activity.getRuleConfig());
            if (config == null || config.getTriggerTypeEnum() == null) {
                continue;
            }

            TriggerType triggerType = config.getTriggerTypeEnum();

            // Skip MONTHLY_CRITERIA — not event-driven
            if (triggerType == TriggerType.MONTHLY_CRITERIA) {
                continue;
            }

            // Match eventType for event-driven types
            if (config.getEventType() != null && !config.getEventType().equals(request.getEventType())) {
                continue;
            }

            // 4. Get strategy and process inside distributed lock
            matchedActivityId = activity.getId();
            ActivityTriggerStrategy strategy = triggerContext.getStrategy(triggerType);

            int awarded = lockHelper.executeWithLock(activity.getId(), request.getUserId(), () -> {
                // Check activity is still ongoing inside lock
                Activity fresh = activityMapper.selectById(activity.getId());
                if (fresh == null || ActivityStatus.ONGOING.getCode() != (int) fresh.getStatus()) {
                    throw new BizException(ErrorCode.ACTIVITY_NOT_ONGOING);
                }
                return strategy.processEvent(request, fresh, config);
            });

            totalAwarded += awarded;
            long rewardAmount = config.getReward() != null && config.getReward().getAmount() != null
                    ? config.getReward().getAmount() : 0L;
            totalReward += awarded * rewardAmount;
        }

        if (matchedActivityId == null) {
            return skippedResponse("No activity matches eventType: " + request.getEventType());
        }

        EventReportResponse response = new EventReportResponse();
        response.setActivityId(matchedActivityId);
        response.setCompletionsAwarded(totalAwarded);
        response.setRewardAmount(totalReward);
        response.setSkipped(false);
        response.setMessage("Processed successfully");
        return response;
    }

    // -------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------

    private EventReportResponse skippedResponse(String message) {
        EventReportResponse response = new EventReportResponse();
        response.setCompletionsAwarded(0);
        response.setRewardAmount(0L);
        response.setSkipped(true);
        response.setMessage(message);
        return response;
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
}
