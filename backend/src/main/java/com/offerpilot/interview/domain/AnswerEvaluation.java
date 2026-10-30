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
@TableName("answer_evaluation")
public class AnswerEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private String promptText;

    private String promptType;

    private Long candidateMessageId;

    private String correctness;

    private Integer score;

    private String comment;

    private String missingPoints;

    private Double confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
