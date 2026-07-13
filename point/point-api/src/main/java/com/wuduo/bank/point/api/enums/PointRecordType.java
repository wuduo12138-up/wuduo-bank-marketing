package com.wuduo.bank.point.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Point record type enum
 */
@Getter
@AllArgsConstructor
public enum PointRecordType {

    /**
     * Activity earn
     */
    ACTIVITY_EARN(1),

    /**
     * Transaction earn
     */
    TRANSACTION_EARN(2),

    /**
     * Sign-in earn
     */
    SIGN_IN_EARN(3),

    /**
     * Mall exchange
     */
    MALL_EXCHANGE(4),

    /**
     * Expire deduct
     */
    EXPIRE_DEDUCT(5),

    /**
     * Manual adjust
     */
    MANUAL_ADJUST(6),

    /**
     * Refund earn
     */
    REFUND_EARN(7);

    private final int code;
}
