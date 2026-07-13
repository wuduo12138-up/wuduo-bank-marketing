package com.wuduo.bank.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Activity version entity — stores full activity configuration snapshots.
 * <p>
 * Every edit creates a new version row. The currently live version is the one
 * with {@code isOnline = 1}, referenced by {@link Activity#getOnlineVersionId()}.
 */
@Data
@TableName("activity_version")
public class ActivityVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    /** Sequential version number, starts at 1 */
    private Integer version;

    /**
     * Full activity configuration as JSON.
     * Contains: title, startTime, endTime, budgetAmount, ruleConfig
     */
    private String content;

    /**
     * Audit status:
     * 0 = draft, 1 = pending audit, 2 = approved, 3 = rejected
     */
    private Integer auditStatus;

    /** 0 = not online, 1 = currently served to users */
    private Integer isOnline;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long createdBy;
}
