package com.wuduo.bank.point.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point record response DTO
 */
@Data
public class PointRecordResponse {

    /**
     * Record ID
     */
    private Long id;

    /**
     * Record number
     */
    private String recordNo;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Point amount
     */
    private Long pointAmount;

    /**
     * Point record type
     * @see com.wuduo.bank.point.api.enums.PointRecordType
     */
    private Integer type;

    /**
     * Business source
     */
    private String bizSource;

    /**
     * Business number
     */
    private String bizNo;

    /**
     * Expire time
     */
    private LocalDateTime expireTime;

    /**
     * Record status
     * @see com.wuduo.bank.point.api.enums.PointRecordStatus
     */
    private Integer status;

    /**
     * Used amount (already deducted for FIFO)
     */
    private Long usedAmount;

    /**
     * Remark
     */
    private String remark;

    /**
     * Created at
     */
    private LocalDateTime createdAt;
}
