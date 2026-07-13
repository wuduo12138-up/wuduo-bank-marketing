package com.wuduo.bank.activity.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Activity trigger type enum — defines how an activity is triggered
 */
@Getter
@AllArgsConstructor
public enum TriggerType {

    SINGLE_TRIGGER(1, "Single Trigger"),
    ACCUMULATION_TRIGGER(2, "Accumulation Trigger"),
    MONTHLY_CRITERIA(3, "Monthly Criteria");

    private final int code;
    private final String description;

    public static TriggerType fromCode(int code) {
        for (TriggerType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TriggerType code: " + code);
    }
}
