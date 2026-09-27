package com.cloudmart.wish.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 注销验证码邮件通道（B20）：SMTP 直发。
 *
 * <p>配置经环境变量注入（wish.mail.*）：</p>
 * <ul>
 *   <li>host/port/username/password/from 任一缺失 → 通道视为未配置，
 *       {@code isConfigured()=false}，发码如实返回 sent=false（不假成功）；</li>
 *   <li>默认 465 端口 SSL（QQ/163 企业邮箱等通用）；</li>
 *   <li>发送失败返回 false 并记日志，调用方向用户如实提示。</li>
 * </ul>
 */
@Component
@Slf4j
public class MailVerificationSender {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;

    private JavaMailSenderImpl sender;

    public MailVerificationSender(
            @Value("${wish.mail.host:${WISH_MAIL_HOST:}}") String host,
            @Value("${wish.mail.port:${WISH_MAIL_PORT:465}}") int port,
            @Value("${wish.mail.username:${WISH_MAIL_USERNAME:}}") String username,
            @Value("${wish.mail.password:${WISH_MAIL_PASSWORD:}}") String password,
            @Value("${wish.mail.from:${WISH_MAIL_FROM:}}") String from) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @PostConstruct
    void init() {
        if (host == null || host.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank() || from == null || from.isBlank()) {
            log.info("B20 邮件验证码通道未配置（wish.mail.*），注销发码将如实返回 sent=false");
            return;
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setUsername(username);
        impl.setPassword(password);
        impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(port == 465));
        props.put("mail.smtp.starttls.enable", String.valueOf(port != 465));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        this.sender = impl;
        log.info("B20 邮件验证码通道已启用: host={}, port={}, from={}", host, port, from);
    }

    public boolean isConfigured() {
        return sender != null;
    }

    /** 发送验证码邮件；失败返回 false（调用方如实提示 sent=false）。 */
    public boolean sendVerificationCode(String toEmail, String code, int ttlMinutes) {
        if (sender == null || toEmail == null || toEmail.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("账号注销验证码");
            helper.setText("您正在申请注销账号。\n\n验证码：" + code
                    + "\n有效期：" + ttlMinutes + " 分钟\n\n如果这不是您本人的操作，请忽略本邮件。");
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("验证码邮件发送失败 to={}（B20：如实返回失败）: {}", toEmail, ex.getMessage());
            return false;
        }
    }
}
