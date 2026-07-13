package com.wuduo.bank.point.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point freeze entity
 */
@Data
@TableName("point_freeze")
public class PointFreeze {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Freeze number (unique)
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
     * Business number
     */
    private String bizNo;

    /**
     * Freeze status (0: frozen, 1: unfrozen, 2: deducted)
     */
    private Integer status;

    /**
     * Remark
     */
    private String remark;

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
