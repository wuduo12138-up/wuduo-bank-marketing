package com.wuduo.bank.rights.application.strategy;

import com.wuduo.bank.rights.domain.entity.RightsDefinition;
import com.wuduo.bank.rights.domain.entity.RightsInstance;

/**
 * Strategy interface for supplier integration.
 * Each implementation handles one supplier type (internal or external).
 */
public interface SupplierStrategy {

    /**
     * @return supplier type code: 0 = internal / own, 1 = external
     */
    Integer supplierType();

    /**
     * Issue the rights instance to the supplier.
     *
     * @param definition the rights definition with supplier config
     * @param instance   the rights instance to issue
     * @return supplier order number (null for internal)
     */
    String issue(RightsDefinition definition, RightsInstance instance);
}
