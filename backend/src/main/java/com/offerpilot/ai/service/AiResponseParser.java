package com.offerpilot.ai.service;

import org.springframework.ai.chat.model.ChatResponse;

@FunctionalInterface
public interface AiResponseParser<T> {

    T parse(ChatResponse response) throws Exception;
}
