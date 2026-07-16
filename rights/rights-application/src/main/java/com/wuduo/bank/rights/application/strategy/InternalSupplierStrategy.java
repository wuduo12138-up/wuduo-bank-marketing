package com.wuduo.bank.rights.application.strategy;

import com.wuduo.bank.rights.domain.entity.RightsDefinition;
import com.wuduo.bank.rights.domain.entity.RightsInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Internal (own-brand) supplier strategy.
 * No external API call needed — rights are managed directly within the platform.
 */
@Slf4j
@Component
public class InternalSupplierStrategy implements SupplierStrategy {

    @Override
    public Integer supplierType() {
        return 0;
    }

    @Override
    public String issue(RightsDefinition definition, RightsInstance instance) {
        log.info("Internal rights issued: instanceNo={}, rightsCode={}, userId={}",
                instance.getInstanceNo(), instance.getRightsCode(), instance.getUserId());
        return null; // No supplier order number for internal rights
    }
}
