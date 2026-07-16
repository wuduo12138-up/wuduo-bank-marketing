package com.wuduo.bank.rights.application.strategy;

import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-managed registry of {@link SupplierStrategy} implementations.
 * Routes to the correct strategy based on supplierType.
 */
@Component
public class SupplierContext {

    private final Map<Integer, SupplierStrategy> strategyMap;

    public SupplierContext(List<SupplierStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(SupplierStrategy::supplierType, Function.identity()));
    }

    /**
     * Get the strategy for a given supplier type.
     *
     * @param supplierType 0=internal, 1=external
     * @return the matching strategy
     * @throws BizException if no strategy is registered for the type
     */
    public SupplierStrategy getStrategy(Integer supplierType) {
        SupplierStrategy strategy = strategyMap.get(supplierType);
        if (strategy == null) {
            throw new BizException(ErrorCode.RIGHTS_SUPPLIER_ERROR, "不支持的供应商类型: " + supplierType);
        }
        return strategy;
    }
}
