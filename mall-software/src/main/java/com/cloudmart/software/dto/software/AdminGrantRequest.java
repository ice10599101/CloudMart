package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 付款查看授权开通/延期请求（方案 6.14）：重开更新同一行并追加审计 */
@Getter
@Setter
public class AdminGrantRequest {

    private Long userId;

    /** 到期时间（可空=长期有效；恰好到期即无效） */
    private LocalDateTime expiresAt;

    /** 管理员人工确认备注（仅有权管理员可见） */
    private String confirmationNote;

    /** 外部核对编号（线下凭证引用） */
    private String externalReference;

    private String reason;
}
