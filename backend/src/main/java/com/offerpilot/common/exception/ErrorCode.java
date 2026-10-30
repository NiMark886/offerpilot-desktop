package com.offerpilot.common.exception;

public enum ErrorCode {
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40301, "无权访问"),
    NOT_FOUND(40401, "资源不存在"),
    CONFLICT(40901, "资源冲突"),
    FILE_TOO_LARGE(41301, "文件过大"),
    AI_UNAVAILABLE(40020, "AI 服务不可用"),
    QUOTA_EXCEEDED(42902, "额度不足"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
