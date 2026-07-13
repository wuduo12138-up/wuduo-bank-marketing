package com.wuduo.bank.mall.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Product category enum
 */
@Getter
@AllArgsConstructor
public enum ProductCategory {

    PHYSICAL(1, "Physical product"),
    VIRTUAL(2, "Virtual product"),
    RIGHTS(3, "Rights product");

    private final Integer code;
    private final String description;

    public static ProductCategory fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductCategory category : values()) {
            if (category.getCode().equals(code)) {
                return category;
            }
        }
        return null;
    }
}
