package com.cloudmart.common.config;

import com.cloudmart.common.mail.MailChannelProperties;
import com.cloudmart.common.mail.MailVerificationSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 平台级邮件验证码通道装配：所有引入 mall-common 的服务获得统一
 * {@link MailVerificationSender}，业务侧按 cloudmart.mail.* 决定是否启用。
 */
@AutoConfiguration
@EnableConfigurationProperties(MailChannelProperties.class)
public class MailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MailVerificationSender mailVerificationSender(MailChannelProperties properties) {
        return new MailVerificationSender(properties);
    }
}
