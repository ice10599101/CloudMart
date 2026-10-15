package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 付款查看授权（方案 5.4/6.14）：一会员+一款软件；管理员线下确认后手动开通；
 * 覆盖该软件当前及后续版本全部来源；过期由服务端当前时间实时判定。
 */
@Getter
@Setter
@TableName("software_access_grant")
public class SoftwareAccessGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    /** ACTIVE/REVOKED */
    private String status;

    private Long grantedBy;

    private LocalDateTime grantedAt;

    /** 到期时间（NULL=长期有效；恰好到期即无效） */
    private LocalDateTime expiresAt;

    private Long revokedBy;

    private LocalDateTime revokedAt;

    /** 管理员人工确认备注（仅有权管理员可见） */
    private String confirmationNote;

    /** 外部核对编号（线下凭证引用，非支付回调） */
    private String externalReference;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
