package com.wuduo.bank.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User activity progress entity — tracks per-user, per-activity, per-period
 * accumulated values and completion counts.
 */
@Data
@TableName("activity_user_progress")
public class ActivityUserProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Activity ID
     */
    private Long activityId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * All-time accumulated event count (for ACCUMULATION_TRIGGER) or
     * current period usage count (for SINGLE_TRIGGER)
     */
    private Long currentValue;

    /**
     * Period identifier, e.g., "2026-07"
     */
    private String periodKey;

    /**
     * Number of completions awarded in the current period
     */
    private Integer completionCount;

    /**
     * Optimistic lock version
     */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
