package com.wuduo.bank.activity.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Activity type enum
 */
@Getter
@AllArgsConstructor
public enum ActivityType {

    SIGN_IN(1, "Sign In"),
    TRANSACTION(2, "Transaction"),
    INVITATION(3, "Invitation"),
    QUIZ(4, "Quiz"),
    EVENT_DRIVEN(5, "Event Driven"),
    MONTHLY_CRITERIA(6, "Monthly Criteria");

    private final int code;
    private final String description;

    public static ActivityType fromCode(int code) {
        for (ActivityType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ActivityType code: " + code);
    }
}
