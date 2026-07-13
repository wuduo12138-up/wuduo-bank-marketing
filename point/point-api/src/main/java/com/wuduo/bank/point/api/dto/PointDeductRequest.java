package com.wuduo.bank.point.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Point deduct request DTO
 */
@Data
public class PointDeductRequest {

    /**
     * User ID
     */
    @NotNull(message = "userId cannot be null")
    private Long userId;

    /**
     * Point amount to deduct
     */
    @NotNull(message = "pointAmount cannot be null")
    private Long pointAmount;

    /**
     * Point record type
     * @see com.wuduo.bank.point.api.enums.PointRecordType
     */
    @NotNull(message = "type cannot be null")
    private Integer type;

    /**
     * Business source
     */
    private String bizSource;

    /**
     * Business number (idempotent key)
     */
    private String bizNo;

    /**
     * Freeze number - if set, deduct from frozen points
     */
    private String freezeNo;
}
