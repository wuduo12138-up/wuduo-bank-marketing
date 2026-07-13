package com.wuduo.bank.point.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Point freeze request DTO
 */
@Data
public class PointFreezeRequest {

    /**
     * User ID
     */
    @NotNull(message = "userId cannot be null")
    private Long userId;

    /**
     * Freeze amount (required for freeze, not needed for unfreeze)
     */
    private Long freezeAmount;

    /**
     * Business number
     */
    @NotNull(message = "bizNo cannot be null")
    private String bizNo;
}
