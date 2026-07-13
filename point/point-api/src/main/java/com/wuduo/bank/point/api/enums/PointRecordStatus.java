package com.wuduo.bank.point.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Point record status enum
 */
@Getter
@AllArgsConstructor
public enum PointRecordStatus {

    /**
     * Valid
     */
    VALID(1),

    /**
     * Expired
     */
    EXPIRED(2),

    /**
     * Used
     */
    USED(3),

    /**
     * Revoked
     */
    REVOKED(4);

    private final int code;
}
