package com.wuduo.bank.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mall product entity
 */
@Data
@TableName("mall_product")
public class MallProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productCode;

    private String name;

    private Integer category;

    private Long pointPrice;

    private BigDecimal originalPrice;

    private Integer totalStock;

    private Integer availableStock;

    private Integer status;

    private Integer displayOrder;

    private String images;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
