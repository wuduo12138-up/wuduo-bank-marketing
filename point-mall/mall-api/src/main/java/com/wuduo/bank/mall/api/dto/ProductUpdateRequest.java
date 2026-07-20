package com.wuduo.bank.mall.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Product update request DTO.
 * All fields are optional — only non-null fields will be merged.
 */
@Data
public class ProductUpdateRequest {

    private String name;

    private Integer category;

    private Long pointPrice;

    private BigDecimal originalPrice;

    private Integer totalStock;

    private String images;

    private String description;

    private Integer displayOrder;

    private String rightsCode;
}
