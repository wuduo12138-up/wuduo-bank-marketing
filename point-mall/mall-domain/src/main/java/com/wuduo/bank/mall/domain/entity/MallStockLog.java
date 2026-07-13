package com.wuduo.bank.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mall stock log entity
 */
@Data
@TableName("mall_stock_log")
public class MallStockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productCode;

    private Integer changeType;

    private Integer changeQuantity;

    private Integer beforeStock;

    private Integer afterStock;

    private String orderNo;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
