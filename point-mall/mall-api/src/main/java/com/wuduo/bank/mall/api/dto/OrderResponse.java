package com.wuduo.bank.mall.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Order response DTO
 */
@Data
public class OrderResponse {

    private Long id;

    private String orderNo;

    private Long userId;

    private String productCode;

    private String productName;

    private Long pointAmount;

    private Integer quantity;

    private Integer status;

    private String deliveryInfo;

    private String rightsCode;

    private String rightsInstanceNo;

    private Integer category;

    private LocalDateTime createdAt;
}
