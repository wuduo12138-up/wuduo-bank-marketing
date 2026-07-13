package com.wuduo.bank.rights.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rights Instance Status Enum
 */
@Getter
@AllArgsConstructor
public enum RightsInstanceStatus {

    PENDING_ACTIVATE(0, "待激活"),
    ACTIVATED(1, "已激活"),
    USED(2, "已使用"),
    EXPIRED(3, "已过期"),
    REVOKED(4, "已撤销");

    private final Integer code;
    private final String description;

    public static RightsInstanceStatus fromCode(Integer code) {
        for (RightsInstanceStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid RightsInstanceStatus code: " + code);
    }
}
