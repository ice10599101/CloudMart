package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.mail.MailVerificationSender;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.service.RegisterCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 注册邮箱验证码服务实现（安全基线与 mall-wish 注销发码一致，见 {@link RegisterCodeService}）。
 *
 * <p>Redis Key（TTL 均必设，符合平台 Redis 规范）：</p>
 * <ul>
 *   <li>{@code user:register:code:{email}} —— 验证码 SHA-256 哈希，TTL 5 分钟；</li>
 *   <li>{@code user:register:code:cooldown:{email}} —— 发送冷却标记，TTL 60 秒；</li>
 *   <li>{@code user:register:code:hourly:{email}} —— 每小时发送计数（INCR），TTL 1 小时。</li>
 * </ul>
 *
 * <p>Redis 故障策略：fail-closed——发码/校验依赖 Redis 存活，Redis 异常时发码抛错、
 * 校验视为无效，绝不放行未经验证的注册。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterCodeServiceImpl implements RegisterCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration HOURLY_TTL = Duration.ofHours(1);
    /** 每邮箱每小时最多发码次数（防邮件轰炸，配合 60 秒冷却） */
    private static final int HOURLY_SEND_LIMIT = 5;
    private static final String CODE_KEY_PREFIX = "user:register:code:";
    private static final String COOLDOWN_KEY_PREFIX = "user:register:code:cooldown:";
    private static final String HOURLY_KEY_PREFIX = "user:register:code:hourly:";

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final MailVerificationSender mailSender;

    private final SecureRandom secureRandom = new SecureRandom();

    /** echo-code 仅供无邮件通道的开发/测试环境回显验证码，生产必须关闭 */
    @Value("${user.register.echo-code:false}")
    private boolean echoCode;

    @Override
    public SendCodeResult sendCode(String rawEmail) {
        final String email = normalizeEmail(rawEmail);

        // 邮箱已注册时提前拒绝，不泄露验证码也不发扰民邮件
        long registered = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (registered > 0) {
            throw new BusinessException("EMAIL_DUPLICATE", "邮箱已被注册");
        }

        Boolean cooldown = redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email);
        if (Boolean.TRUE.equals(cooldown)) {
            throw new BusinessException("USER_REGISTER_CODE_FREQUENT", "验证码发送过于频繁，请稍后再试");
        }
        Long hourlyCount = redisTemplate.opsForValue().increment(HOURLY_KEY_PREFIX + email);
        if (hourlyCount != null) {
            if (hourlyCount == 1) {
                redisTemplate.expire(HOURLY_KEY_PREFIX + email, HOURLY_TTL);
            }
            if (hourlyCount > HOURLY_SEND_LIMIT) {
                throw new BusinessException("USER_REGISTER_CODE_FREQUENT",
                        "验证码发送次数已达上限，请 1 小时后再试");
            }
        }

        final String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // echo-code 仅开发/测试回显（生产必须关闭）
        if (echoCode) {
            redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, sha256(code), CODE_TTL);
            log.warn("注册验证码回显模式（仅开发/测试）email={}", email);
            return new SendCodeResult(true, code, null);
        }

        if (!mailSender.isConfigured()) {
            return new SendCodeResult(false, null, "验证码邮件通道未配置，请联系管理员");
        }

        redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + email, "1", COOLDOWN_TTL);
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, sha256(code), CODE_TTL);
        boolean sent = mailSender.send(email, "宝贝小答注册验证码",
                "您正在注册宝贝小答账号。\n\n验证码：" + code
                        + "\n有效期：" + CODE_TTL.toMinutes() + " 分钟\n\n如果这不是您本人的操作，请忽略本邮件。");
        if (sent) {
            log.info("注册验证码邮件已发送 email={}", maskEmail(email));
            return new SendCodeResult(true, null, null);
        }
        // 发送失败：清掉验证码避免半成功（用户重试会重新生成）
        redisTemplate.delete(CODE_KEY_PREFIX + email);
        return new SendCodeResult(false, null, "验证码邮件发送失败，请稍后重试");
    }

    @Override
    public void verifyAndConsume(String rawEmail, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessException("USER_REGISTER_CODE_INVALID", "验证码须为 6 位数字");
        }
        final String email = normalizeEmail(rawEmail);
        final String cachedHash = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
        if (cachedHash == null || !cachedHash.equals(sha256(code))) {
            throw new BusinessException("USER_REGISTER_CODE_INVALID", "验证码无效或已过期");
        }
        redisTemplate.delete(CODE_KEY_PREFIX + email);
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "邮箱不能为空");
        }
        return rawEmail.trim().toLowerCase();
    }

    private String sha256(String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** 日志脱敏：仅保留首字符与域名，避免 PII 落日志 */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
