package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户同意记录（文档 1.2 节 ⑳，GDPR / 个保法留痕）。
 *
 * <p>唯一约束 {@code uk_consent_unique(user_id, consent_type, version, action)} 防重复记录，
 * 有效性判定取同类型最新一条记录的 action。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_consent")
public class WishConsent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private ConsentType consentType;

    /** 协议版本号（每次协议更新递增） */
    private String version;

    /** 同意时协议文本 SHA-256 哈希（防篡改） */
    private String consentTextHash;

    private ConsentAction action;

    /** 操作 IP（IPv4/IPv6） */
    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
