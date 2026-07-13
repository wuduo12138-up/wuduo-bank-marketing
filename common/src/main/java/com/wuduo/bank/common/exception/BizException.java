package com.wuduo.bank.common.exception;

import com.wuduo.bank.common.model.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String extraMessage) {
        super(errorCode.getMessage() + ": " + extraMessage);
        this.code = errorCode.getCode();
    }
}
