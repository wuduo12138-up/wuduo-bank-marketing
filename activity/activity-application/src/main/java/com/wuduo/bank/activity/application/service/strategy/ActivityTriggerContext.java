package com.wuduo.bank.activity.application.service.strategy;

import com.wuduo.bank.activity.api.enums.TriggerType;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-managed registry of {@link ActivityTriggerStrategy} implementations.
 * Routes to the correct strategy based on TriggerType.
 */
@Component
public class ActivityTriggerContext {

    private final Map<TriggerType, ActivityTriggerStrategy> strategyMap;

    public ActivityTriggerContext(List<ActivityTriggerStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ActivityTriggerStrategy::supportedType, Function.identity()));
    }

    /**
     * Get the strategy for a given trigger type.
     *
     * @param type the trigger type
     * @return the matching strategy
     * @throws BizException if no strategy is registered for the type
     */
    public ActivityTriggerStrategy getStrategy(TriggerType type) {
        ActivityTriggerStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new BizException(ErrorCode.ACTIVITY_TRIGGER_TYPE_INVALID);
        }
        return strategy;
    }
}
