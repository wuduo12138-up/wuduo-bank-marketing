package com.wuduo.bank.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity entity corresponding to the activity table
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    /**
     * Points to the currently online {@link ActivityVersion#getId()}.
     * {@code null} before the initial audit is approved.
     */
    private Long onlineVersionId;

    private Integer deleted;
}
