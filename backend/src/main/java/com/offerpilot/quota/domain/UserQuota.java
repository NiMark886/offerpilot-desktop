package com.offerpilot.quota.domain;

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
@TableName("user_quota")
public class UserQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String plan;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Integer sessionLimit;

    private Integer sessionUsed;

    private Integer tokenLimit;

    private Integer tokenUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
