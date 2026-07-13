package com.wuduo.bank.rights.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rights Definition Entity
 */
@Data
@TableName("rights_definition")
public class RightsDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权益编码
     */
    private String rightsCode;

    /**
     * 权益名称
     */
    private String name;

    /**
     * 权益类型: 1-优惠券 2-利率券 3-VIP服务 4-实物 5-第三方权益
     */
    private Integer type;

    /**
     * 供应商类型
     */
    private Integer supplierType;

    /**
     * 供应商编码
     */
    private String supplierCode;

    /**
     * 总库存
     */
    private Integer totalStock;

    /**
     * 已使用库存
     */
    private Integer usedStock;

    /**
     * 有效天数
     */
    private Integer validDays;

    /**
     * 状态: 0-禁用 1-启用
     */
    private Integer status;

    /**
     * 回调地址
     */
    private String callbackUrl;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除: 0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
