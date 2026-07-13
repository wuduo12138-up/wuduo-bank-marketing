package com.wuduo.bank.mall.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Product response DTO
 */
@Data
public class ProductResponse {

    private Long id;

    private String productCode;

    private String name;

    private Integer category;

    private Long pointPrice;

    private BigDecimal originalPrice;

    private Integer totalStock;

    private Integer availableStock;

    private Integer status;

    private Integer displayOrder;

    private String images;

    private String description;
}
