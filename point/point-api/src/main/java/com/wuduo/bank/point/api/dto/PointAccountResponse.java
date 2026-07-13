package com.wuduo.bank.point.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Point account response DTO
 */
@Data
public class PointAccountResponse {

    /**
     * User ID
     */
    private Long userId;

    /**
     * Total earned points
     */
    private Long totalEarned;

    /**
     * Total used points
     */
    private Long totalUsed;

    /**
     * Total expired points
     */
    private Long totalExpired;

    /**
     * Frozen points
     */
    private Long frozen;

    /**
     * Available points
     */
    private Long available;
}
