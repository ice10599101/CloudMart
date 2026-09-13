package com.cloudmart.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 全站广播请求（JSON body 传输）。
 *
 * <p>历史教训：broadcast 曾用 @RequestParam 传参——mall-common XssFilter 会对 query 参数
 * 做 HTML 转义（&lt; → &amp;lt;），富文本公告落库即成"乱码"；且 URL query 有长度上限，
 * 长公告会被截断。body 传输两者皆避。</p>
 */
public record BroadcastNotificationRequest(
    @NotBlank(message = "通知类型不能为空")
    @Size(max = 30, message = "通知类型最长 30 字符")
    String type,

    @NotBlank(message = "通知标题不能为空")
    @Size(max = 100, message = "通知标题最长 100 字符")
    String title,

    @NotBlank(message = "通知内容不能为空")
    @Size(max = 60000, message = "通知内容过长")
    String content
) {}
