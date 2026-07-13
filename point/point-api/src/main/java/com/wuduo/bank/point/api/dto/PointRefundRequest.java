package com.wuduo.bank.point.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Point refund request DTO
 */
@Data
public class PointRefundRequest {

    /**
     * User ID
     */
    @NotNull(message = "userId cannot be null")
    private Long userId;

    /**
     * Original record number to refund
     */
    @NotBlank(message = "originalRecordNo cannot be blank")
    private String originalRecordNo;

    /**
     * Remark
     */
    private String remark;
}
