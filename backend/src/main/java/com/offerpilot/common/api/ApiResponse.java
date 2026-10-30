package com.offerpilot.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", data, MDC.get("traceId"));
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "OK", null, MDC.get("traceId"));
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"));
    }
}
