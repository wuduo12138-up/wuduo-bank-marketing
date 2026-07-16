package com.wuduo.bank.rights.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rights Issue Log Entity
 */
@Data
@TableName("rights_issue_log")
public class RightsIssueLog implements Serializable {

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
     * 操作类型: 1-发放 2-激活 3-使用 4-过期 5-撤销
     */
    private Integer operationType;

    /**
     * 操作结果: 0-失败 1-成功
     */
    private Integer operationResult;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除: 0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
