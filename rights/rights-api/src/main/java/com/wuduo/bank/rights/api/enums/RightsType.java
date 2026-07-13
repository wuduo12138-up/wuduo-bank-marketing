package com.wuduo.bank.rights.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rights Type Enum
 */
@Getter
@AllArgsConstructor
public enum RightsType {

    COUPON(1, "优惠券"),
    RATE_COUPON(2, "利率券"),
    VIP_SERVICE(3, "VIP服务"),
    PHYSICAL(4, "实物"),
    THIRD_PARTY(5, "第三方权益");

    private final Integer code;
    private final String description;

    public static RightsType fromCode(Integer code) {
        for (RightsType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid RightsType code: " + code);
    }
}
