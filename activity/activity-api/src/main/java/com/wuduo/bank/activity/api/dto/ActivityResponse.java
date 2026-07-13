package com.wuduo.bank.activity.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity response DTO
 */
@Data
public class ActivityResponse {

    private Long id;

    private String activityCode;

    private String title;

    private Integer type;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal budgetAmount;

    private BigDecimal budgetUsed;

    private String ruleConfig;

    private LocalDateTime createdAt;

    private Long onlineVersionId;

    private Integer currentVersion;

    /**
     * Version edit status: ONLINE(线上版本), DRAFT(草稿), PENDING_AUDIT(审批中), REJECTED(审批被驳回)
     */
    private String versionStatus;
}
