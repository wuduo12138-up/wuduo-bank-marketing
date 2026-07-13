package com.wuduo.bank.common.exception;

/**
 * 参数校验异常
 */
public class ParamException extends RuntimeException {

    public ParamException(String message) {
        super(message);
    }
}
