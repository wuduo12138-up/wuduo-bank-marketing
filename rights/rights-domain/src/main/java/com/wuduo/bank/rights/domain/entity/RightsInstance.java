package com.wuduo.bank.rights.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rights Instance Entity
 */
@Data
@TableName("rights_instance")
public class RightsInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权益实例编号
     */
    private String instanceNo;

    /**
     * 权益编码
     */
    private String rightsCode;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 来源类型: 1-活动 2-商城兑换 3-人工发放
     */
    private Integer sourceType;

    /**
     * 来源单号
     */
    private String sourceNo;

    /**
     * 状态: 0-待激活 1-已激活 2-已使用 3-已过期 4-已撤销
     */
    private Integer status;

    /**
     * 激活时间
     */
    private LocalDateTime activateTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 使用时间
     */
    private LocalDateTime useTime;

    /**
     * 供应商订单号
     */
    private String supplierOrderNo;

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
