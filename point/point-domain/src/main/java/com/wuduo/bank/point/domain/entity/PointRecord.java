package com.wuduo.bank.point.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point record entity
 */
@Data
@TableName("point_record")
public class PointRecord {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
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
     * Point amount (positive for earn, negative for deduct)
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
     * Already deducted amount (for FIFO partial deductions)
     */
    private Long usedAmount;

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
