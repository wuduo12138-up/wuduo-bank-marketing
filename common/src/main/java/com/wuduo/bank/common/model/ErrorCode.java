package com.wuduo.bank.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * 规范: 5位数字, 前两位为模块编号
 * 10xxx - 活动
 * 20xxx - 积分
 * 30xxx - 商城
 * 40xxx - 权益
 * 50xxx - 通用
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用错误
    SUCCESS("200", "成功"),
    BAD_REQUEST("400", "参数错误"),
    UNAUTHORIZED("401", "未认证"),
    FORBIDDEN("403", "无权限"),
    NOT_FOUND("404", "资源不存在"),
    INTERNAL_ERROR("500", "系统内部错误"),
    TOO_MANY_REQUESTS("429", "请求过于频繁"),

    // 活动 10xxx
    ACTIVITY_NOT_FOUND("10001", "活动不存在"),
    ACTIVITY_ENDED("10002", "活动已结束"),
    ACTIVITY_BUDGET_INSUFFICIENT("10003", "活动预算不足"),
    ACTIVITY_STATUS_INVALID("10004", "活动状态不合法"),
    ACTIVITY_DUPLICATE_PARTICIPATION("10005", "重复参与活动"),
    ACTIVITY_EVENT_DUPLICATE("10006", "重复的事件"),
    ACTIVITY_FREQUENCY_EXCEEDED("10007", "活动频率已达上限"),
    ACTIVITY_NOT_ONGOING("10008", "活动未在进行中"),
    ACTIVITY_TRIGGER_TYPE_INVALID("10009", "无效的触发器类型"),
    ACTIVITY_CRITERIA_SERVICE_ERROR("10010", "外部达标服务调用失败"),
    ACTIVITY_PROGRESS_NOT_FOUND("10011", "活动进度记录不存在"),
    ACTIVITY_REWARD_FAILED("10012", "活动奖励发放失败"),
    ACTIVITY_EVENT_DATA_PARSE_ERROR("10013", "事件数据解析失败"),

    // 积分 20xxx
    POINT_BALANCE_INSUFFICIENT("20001", "积分余额不足"),
    POINT_FREEZE_FAILED("20002", "积分冻结失败"),
    POINT_ACCOUNT_NOT_FOUND("20003", "积分账户不存在"),
    POINT_DUPLICATE_ISSUE("20004", "重复积分发放"),
    POINT_FREEZE_NOT_FOUND("20005", "冻结记录不存在"),
    POINT_FREEZE_STATUS_INVALID("20006", "冻结状态不合法"),
    POINT_VERSION_CONFLICT("20007", "版本冲突，请重试"),
    POINT_RECORD_NOT_FOUND("20008", "积分流水不存在"),
    POINT_RECORD_STATUS_INVALID("20009", "积分流水状态不合法"),
    POINT_FREEZE_AMOUNT_INSUFFICIENT("20010", "冻结积分不足"),

    // 商城 30xxx
    MALL_STOCK_INSUFFICIENT("30001", "库存不足"),
    MALL_ORDER_NOT_FOUND("30002", "订单不存在"),
    MALL_PRODUCT_NOT_FOUND("30003", "商品不存在"),
    MALL_PRODUCT_OFF_SHELF("30004", "商品已下架"),

    // 权益 40xxx
    RIGHTS_STOCK_INSUFFICIENT("40001", "权益库存不足"),
    RIGHTS_ISSUE_FAILED("40002", "权益发放失败"),
    RIGHTS_NOT_FOUND("40003", "权益不存在"),
    RIGHTS_EXPIRED("40004", "权益已过期");

    private final String code;
    private final String message;
}
