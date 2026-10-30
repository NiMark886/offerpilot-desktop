package com.offerpilot.ai.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long userId;

    private Long sessionId;

    private String scene;

    private String promptVersion;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Integer success;

    private Integer fallback;

    private String errorCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
