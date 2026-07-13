package com.wuduo.bank.common.config;

import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.exception.ParamException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: uri={}, code={}, msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        R<Void> r = R.fail(e.getCode(), e.getMessage());
        r.setTraceId(getTraceId(request));
        return r;
    }

    @ExceptionHandler(ParamException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleParamException(ParamException e, HttpServletRequest request) {
        log.warn("参数异常: uri={}, msg={}", request.getRequestURI(), e.getMessage());
        R<Void> r = R.fail(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        r.setTraceId(getTraceId(request));
        return r;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: uri={}, msg={}", request.getRequestURI(), message);
        R<Void> r = R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
        r.setTraceId(getTraceId(request));
        return r;
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: uri={}, msg={}", request.getRequestURI(), message);
        R<Void> r = R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
        r.setTraceId(getTraceId(request));
        return r;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: uri={}", request.getRequestURI(), e);
        R<Void> r = R.fail(ErrorCode.INTERNAL_ERROR);
        r.setTraceId(getTraceId(request));
        return r;
    }

    private String getTraceId(HttpServletRequest request) {
        return request.getHeader("X-Trace-Id");
    }
}
