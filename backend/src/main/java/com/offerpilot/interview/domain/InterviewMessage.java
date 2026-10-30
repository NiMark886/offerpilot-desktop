package com.offerpilot.interview.domain;

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
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private String role;

    private String messageType;

    private String content;

    private Integer tokensIn;

    private Integer tokensOut;

    private String model;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
