package com.example.kb.common.result;

import lombok.Data;

/**
 * 统一返回体。
 */
@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.msg = ResultCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return fail(resultCode, resultCode.getMsg());
    }

    public static <T> R<T> fail(ResultCode resultCode, String msg) {
        R<T> r = new R<>();
        r.code = resultCode.getCode();
        r.msg = msg;
        return r;
    }
}
