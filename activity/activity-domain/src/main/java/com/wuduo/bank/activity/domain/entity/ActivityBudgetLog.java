package com.wuduo.bank.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity budget log entity corresponding to the activity_budget_log table
 */
@Data
@TableName("activity_budget_log")
public class ActivityBudgetLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private BigDecimal amount;

    private Integer type;

    private String remark;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer deleted;
}
