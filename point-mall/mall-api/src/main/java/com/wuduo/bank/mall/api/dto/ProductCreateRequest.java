package com.wuduo.bank.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Product create request DTO
 */
@Data
public class ProductCreateRequest {

    @NotBlank(message = "Product name cannot be blank")
    private String name;

    @NotNull(message = "Product category cannot be null")
    private Integer category;

    @NotNull(message = "Point price cannot be null")
    private Long pointPrice;

    private BigDecimal originalPrice;

    @NotNull(message = "Total stock cannot be null")
    private Integer totalStock;

    private String images;

    private String description;

    private String rightsCode;
}
