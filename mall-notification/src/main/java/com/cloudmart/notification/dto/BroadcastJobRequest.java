package com.cloudmart.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 通知群发批次提交请求（ADMIN-03）。
 *
 * <p>{@code requestId} 为提交幂等键：同键重复提交返回原批次，不重复投递。
 * 受众固定为全站用户（禁用账号跳过），不支持自选分组。</p>
 */
public record BroadcastJobRequest(
        @NotNull(message = "通知类型不能为空")
        @Size(max = 30, message = "通知类型最长 30 字符")
        String type,

        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题最长 100 字符")
        String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 60000, message = "通知内容过长")
        String content,

        @NotNull(message = "requestId 不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "requestId 须为 8-64 位字母/数字/下划线/连字符")
        String requestId) {
}
