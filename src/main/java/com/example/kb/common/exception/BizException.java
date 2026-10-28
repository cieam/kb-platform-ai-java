package com.example.kb.common.exception;

import com.example.kb.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转成 R.fail。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public static BizException of(String message) {
        return new BizException(ResultCode.BIZ_ERROR, message);
    }
}
