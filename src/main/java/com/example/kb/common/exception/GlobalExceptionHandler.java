package com.example.kb.common.exception;

import com.example.kb.common.result.R;
import com.example.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底。
 * 原则：业务异常打 warn 并返回原始错误码；未知异常打 error，
 * 对外只回「系统繁忙」，不把堆栈和内部信息暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleValidation(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse(ResultCode.PARAM_ERROR.getMsg());
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }
}
