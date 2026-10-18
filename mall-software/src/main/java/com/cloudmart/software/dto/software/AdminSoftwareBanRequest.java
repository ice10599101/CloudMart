package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端封禁/解除请求（方案 7.5/10.1）：
 * 封禁与解除必须携带原因；并发由服务端行锁与原状态判断保证。
 * 解除回到 UNLISTED，由作者恢复展示。
 */
@Getter
@Setter
public class AdminSoftwareBanRequest {

    /** 处置原因（1~500 字，审计与通知可见） */
    private String reason;
}
