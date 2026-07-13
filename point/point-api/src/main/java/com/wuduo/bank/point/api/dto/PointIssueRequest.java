package com.wuduo.bank.point.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point issue request DTO
 */
@Data
public class PointIssueRequest {

    /**
     * User ID
     */
    @NotNull(message = "userId cannot be null")
    private Long userId;

    /**
     * Point amount
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
     * Business number
     */
    private String bizNo;

    /**
     * Expire time
     */
    private LocalDateTime expireTime;
}
