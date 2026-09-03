package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 账号注销宽限期（合规 34.2 / API 2.13，文档表⑲）。
 *
 * <p>1 用户 1 条记录（uk_deletion_user）：PENDING（30 天宽限期）→
 * CANCELED（撤回）/ EXECUTED（到期执行）。验证码本体仅存 Redis（TTL 5min），
 * 本表 code_hash 仅供审计。</p>
 */
@Getter
@Setter
@TableName("wish_account_deletion")
public class WishAccountDeletion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** PENDING / CANCELED / EXECUTED */
    private String status;

    /** 注销原因（可选，最长 500） */
    private String reason;

    private LocalDateTime requestedAt;

    /** 实际执行时间（requested_at + 30 天宽限期） */
    private LocalDateTime executeAfter;

    private LocalDateTime canceledAt;

    private LocalDateTime executedAt;

    /** 验证码 SHA-256 哈希（验证用审计字段） */
    private String codeHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
