package com.cloudmart.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台级邮件验证码通道配置（cloudmart.mail.*）。
 *
 * <p>各服务在自身 application.yml 中将本前缀映射到部署环境变量（如注册用
 * {@code CLOUDMART_MAIL_*}，注销保留 {@code WISH_MAIL_*} 兼容回退），
 * 任一必填项缺失即视为通道未配置。</p>
 */
@ConfigurationProperties(prefix = "cloudmart.mail")
public record MailChannelProperties(String host, int port, String username, String password, String from) {

    public MailChannelProperties {
        if (port <= 0) {
            port = 465;
        }
    }

    /** host/username/password/from 任一缺失即通道不可用（不假成功，调用方如实提示） */
    public boolean isComplete() {
        return host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank()
                && from != null && !from.isBlank();
    }
}
