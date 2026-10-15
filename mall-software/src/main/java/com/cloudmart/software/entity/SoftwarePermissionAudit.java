package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限变更审计（方案 6.14）：发布资格/付款授权/联系入口操作历史；
 * 只追加不设 updated_at；状态快照不含下载链接/支付敏感信息。
 */
@Getter
@Setter
@TableName("software_permission_audit")
public class SoftwarePermissionAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标会员（发布资格/授权操作时） */
    private Long targetUserId;

    private Long softwareId;

    private Long contactId;

    private Long actorAdminId;

    /** PUBLISH_ENABLE/PUBLISH_DISABLE/ACCESS_GRANT/ACCESS_REVOKE/ACCESS_EXTEND/CONTACT_UPDATE */
    private String action;

    private String beforeJson;

    private String afterJson;

    private String reason;

    /** 请求追踪 ID（Idempotency-Key 关联） */
    private String requestId;

    private LocalDateTime createdAt;
}
