package com.offerpilot.support;

import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

/**
 * MockMvc 的 asyncDispatch 与 SSE 写线程存在竞态：写线程还在向
 * MockHttpServletResponse 发送事件时触发 dispatch 会抛
 * ConcurrentModificationException。等待异步结果完成后再 dispatch。
 */
public final class SseTestSupport {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private SseTestSupport() {
    }

    public static MvcResult awaitStreamCompletion(MvcResult result) {
        try {
            result.getAsyncResult(TIMEOUT_MILLIS);
        } catch (IllegalStateException ex) {
            // 异步处理尚未开始或超时，交由 asyncDispatch 暴露真实错误
        }
        return result;
    }
}
