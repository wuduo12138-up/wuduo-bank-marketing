package com.wuduo.bank.mall.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Order status enum
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    PENDING(0, "Pending"),
    EXCHANGED(1, "Exchanged"),
    DELIVERING(2, "Delivering"),
    COMPLETED(3, "Completed"),
    CANCELLED(4, "Cancelled"),
    REFUNDED(5, "Refunded");

    private final Integer code;
    private final String description;

    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
