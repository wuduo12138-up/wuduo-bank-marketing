package com.wuduo.bank.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Order create request DTO
 */
@Data
public class OrderCreateRequest {

    @NotBlank(message = "Product code cannot be blank")
    private String productCode;

    @NotNull(message = "Quantity cannot be null")
    private Integer quantity;

    private String deliveryInfo;
}
