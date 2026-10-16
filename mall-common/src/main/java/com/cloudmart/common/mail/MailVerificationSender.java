package com.cloudmart.common.mail;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 平台级验证码邮件通道：SMTP 直发（自 mall-wish 注销发码通道泛化下沉，
 * 注销/注册等业务共用，主题与正文由调用方按业务语义传入）。
 *
 * <p>行为约定：</p>
 * <ul>
 *   <li>cloudmart.mail.* 任一必填项缺失 → 通道未配置，{@code isConfigured()=false}，
 *       发送如实返回 false（不假成功）；</li>
 *   <li>默认 465 端口 SSL（QQ/163 企业邮箱等通用），其他端口 STARTTLS；</li>
 *   <li>发送失败返回 false 并记日志，由调用方向用户如实提示。</li>
 * </ul>
 */
@Slf4j
public class MailVerificationSender {

    private final MailChannelProperties properties;

    private JavaMailSenderImpl sender;

    public MailVerificationSender(MailChannelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.isComplete()) {
            log.info("邮件验证码通道未配置（cloudmart.mail.*），邮件发码将如实返回 false");
            return;
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(properties.host());
        impl.setPort(properties.port());
        impl.setUsername(properties.username());
        impl.setPassword(properties.password());
        impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(properties.port() == 465));
        props.put("mail.smtp.starttls.enable", String.valueOf(properties.port() != 465));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        this.sender = impl;
        log.info("邮件验证码通道已启用: host={}, port={}, from={}",
                properties.host(), properties.port(), properties.from());
    }

    public boolean isConfigured() {
        return sender != null;
    }

    /** 发送邮件；失败返回 false（调用方如实向用户提示）。 */
    public boolean send(String toEmail, String subject, String body) {
        if (sender == null || toEmail == null || toEmail.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            // 完整堆栈随异常参数落日志，保留深层根源，避免只有顶层 Message 无法定位
            log.warn("验证码邮件发送失败 to={}（如实返回失败）: {}", toEmail, rootMessage(ex), ex);
            if (isAuthFailure(ex)) {
                log.warn("SMTP 认证失败：请核对 cloudmart.mail.username/password"
                        + "（QQ/163 等邮箱需使用授权码，而非登录密码；from 须与发件账号一致）");
            }
            return false;
        }
    }

    /** 提取异常链最深处的 message，顶层无信息时兜底用自身 message。 */
    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == ex ? ex.getMessage() : root.getMessage();
    }

    /** SMTP 认证失败识别：535/AuthenticationFailed，用于给运维可操作的日志提示。 */
    private boolean isAuthFailure(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof MailAuthenticationException
                    || current instanceof jakarta.mail.AuthenticationFailedException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && (message.contains("535") || message.toLowerCase(java.util.Locale.ROOT).contains("authentication"))) {
                return true;
            }
        }
        return false;
    }
}
