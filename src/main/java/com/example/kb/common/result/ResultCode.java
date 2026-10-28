package com.example.kb.common.result;

/**
 * 统一错误码。前端只认 code，不解析 msg。
 */
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    BIZ_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(999, "系统繁忙，请稍后再试");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
