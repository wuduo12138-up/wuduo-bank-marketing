package com.wuduo.bank.activity.application.service.strategy;

import com.wuduo.bank.activity.api.dto.EventReportRequest;
import com.wuduo.bank.activity.api.dto.RuleConfig;
import com.wuduo.bank.activity.api.enums.TriggerType;
import com.wuduo.bank.activity.domain.entity.Activity;

/**
 * Strategy interface for activity trigger processing.
 * Each implementation handles one {@link TriggerType}.
 */
public interface ActivityTriggerStrategy {

    /**
     * @return the TriggerType this strategy handles
     */
    TriggerType supportedType();

    /**
     * Process a business event against a matched activity.
     * <p>
     * Called inside a distributed lock for (activityId, userId) and
     * inside the {@code @Transactional} boundary of EventServiceImpl.
     *
     * @param request    the event report request
     * @param activity   the matched activity entity
     * @param ruleConfig parsed rule configuration
     * @return number of new completions awarded by this event (0 if skipped/duplicate)
     */
    int processEvent(EventReportRequest request, Activity activity, RuleConfig ruleConfig);
}
