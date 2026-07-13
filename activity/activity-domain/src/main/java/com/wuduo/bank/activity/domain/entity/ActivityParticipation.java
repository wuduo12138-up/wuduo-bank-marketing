package com.wuduo.bank.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity participation entity corresponding to the activity_participation table
 */
@Data
@TableName("activity_participation")
public class ActivityParticipation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    private Integer participationType;

    private BigDecimal rewardAmount;

    private Integer status;

    private String remark;

    /**
     * Business number for idempotency (e.g., event bizNo)
     */
    private String bizNo;

    /**
     * Period key (e.g., "2026-07")
     */
    private String periodKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
