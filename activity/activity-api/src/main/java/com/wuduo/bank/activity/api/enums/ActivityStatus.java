package com.wuduo.bank.activity.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Activity status enum
 */
@Getter
@AllArgsConstructor
public enum ActivityStatus {

    DRAFT(0, "Draft"),
    PENDING_AUDIT(1, "Pending Audit"),
    REJECTED(2, "Rejected"),
    PUBLISHED(3, "Published"),
    ONGOING(4, "Ongoing"),
    ENDED(5, "Ended"),
    CANCELLED(6, "Cancelled");

    private final int code;
    private final String description;

    public static ActivityStatus fromCode(int code) {
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ActivityStatus code: " + code);
    }
}
