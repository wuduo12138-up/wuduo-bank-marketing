package com.wuduo.bank.point.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Point freeze status enum
 */
@Getter
@AllArgsConstructor
public enum PointFreezeStatus {

    /**
     * Frozen
     */
    FROZEN(0),

    /**
     * Unfrozen
     */
    UNFROZEN(1),

    /**
     * Deducted from frozen
     */
    DEDUCTED(2);

    private final int code;
}
