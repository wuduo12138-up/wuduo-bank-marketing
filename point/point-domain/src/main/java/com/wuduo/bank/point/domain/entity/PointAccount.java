package com.wuduo.bank.point.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point account entity
 */
@Data
@TableName("point_account")
public class PointAccount {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

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

    /**
     * Version for optimistic lock (managed explicitly in application layer)
     */
    private Integer version;

    /**
     * Created at
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Updated at
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
