package com.wuduo.bank.point.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point freeze response DTO
 */
@Data
public class PointFreezeResponse {

    /**
     * Freeze record ID
     */
    private Long id;

    /**
     * Freeze number
     */
    private String freezeNo;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Freeze amount
     */
    private Long freezeAmount;

    /**
     * Freeze status
     * @see com.wuduo.bank.point.api.enums.PointFreezeStatus
     */
    private Integer status;

    /**
     * Business number
     */
    private String bizNo;

    /**
     * Remark
     */
    private String remark;

    /**
     * Created at
     */
    private LocalDateTime createdAt;
}
