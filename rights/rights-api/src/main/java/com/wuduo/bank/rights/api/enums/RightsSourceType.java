package com.wuduo.bank.rights.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rights Source Type Enum
 */
@Getter
@AllArgsConstructor
public enum RightsSourceType {

    ACTIVITY(1, "活动"),
    MALL_EXCHANGE(2, "商城兑换"),
    MANUAL(3, "人工发放");

    private final Integer code;
    private final String description;

    public static RightsSourceType fromCode(Integer code) {
        for (RightsSourceType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid RightsSourceType code: " + code);
    }
}
