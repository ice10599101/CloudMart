package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 来源状态处置请求（方案 4.3/5.3，管理员）：核实结论按指纹匹配应用；
 * DISABLED 无条件生效；解除禁用由管理员单独执行。
 */
@Getter
@Setter
public class SoftwareSourceStateRequest {

    private String sourceKey;

    /** UNKNOWN/AVAILABLE/SUSPECTED/INVALID/DISABLED */
    private String availability;

    /** 处置原因（审计要求，1~500 字） */
    private String reason;
}
