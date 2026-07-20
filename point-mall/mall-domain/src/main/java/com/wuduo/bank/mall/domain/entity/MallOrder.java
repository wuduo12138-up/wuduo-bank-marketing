package com.wuduo.bank.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mall order entity
 */
@Data
@TableName("mall_order")
public class MallOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String productCode;

    private String productName;

    private Long pointAmount;

    private Integer quantity;

    private Integer status;

    private String deliveryInfo;

    private String rightsCode;

    private String rightsInstanceNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
