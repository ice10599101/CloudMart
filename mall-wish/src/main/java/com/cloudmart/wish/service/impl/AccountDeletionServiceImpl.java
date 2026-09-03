package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAccountDeletion;
import com.cloudmart.wish.repository.WishAccountDeletionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/**
 * 账号注销宽限期服务实现（合规 34.2 / API 2.13，四AB A1）。
 *
 * <p>验证码：6 位数字，Redis 存 SHA-256 哈希（TTL 5 分钟），DB 仅存哈希。
 * echo-code 配置仅供无短信/邮件通道的开发/测试环境回显验证码，生产必须关闭。
 * 宽限期 30 天；到期执行：心愿逻辑删除（保留审计），mall-user 账号禁用为
 * 跨服务联动（经内部接口/事件，接口就绪后接入）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final long GRACE_DAYS = 30;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String CODE_KEY_PREFIX = "wish:account-deletion:code:";

    private final WishAccountDeletionMapper deletionMapper;
    private final WishMapper wishMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${wish.account-deletion.echo-code:false}")
    private boolean echoCode;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String sendDeletionCode(Long userId) {
        // 已执行的注销不再发码
        final WishAccountDeletion existing = getByUser(userId);
        if (existing != null && "EXECUTED".equals(existing.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_DELETION_EXECUTED, "账号已注销");
        }
        final String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + userId, sha256(code), CODE_TTL);
        log.info("注销验证码已生成 userId={}（5 分钟有效；生产环境经短信/邮件通道下发）", userId);
        return echoCode ? code : null;
    }

    @Override
    @Transactional
    public WishAccountDeletion apply(Long userId, String confirmCode, String reason) {
        if (confirmCode == null || !confirmCode.matches("\\d{6}")) {
            throw new BusinessException("WISH_CONFIRM_CODE_INVALID", "验证码须为 6 位数字");
        }
        final String cachedHash = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + userId);
        if (cachedHash == null || !cachedHash.equals(sha256(confirmCode))) {
            throw new BusinessException("WISH_CONFIRM_CODE_INVALID", "验证码无效或已过期");
        }
        redisTemplate.delete(CODE_KEY_PREFIX + userId);

        final WishAccountDeletion existing = getByUser(userId);
        if (existing != null) {
            if ("EXECUTED".equals(existing.getStatus())) {
                throw new BusinessException(WishErrorCodes.WISH_DELETION_EXECUTED, "账号已注销");
            }
            if ("PENDING".equals(existing.getStatus())) {
                throw new BusinessException("WISH_DELETION_PENDING", "已存在待执行的注销申请，可撤回后重新申请");
            }
        }

        final WishAccountDeletion deletion = new WishAccountDeletion();
        deletion.setUserId(userId);
        deletion.setStatus("PENDING");
        deletion.setReason(reason == null ? null : reason.trim());
        deletion.setRequestedAt(LocalDateTime.now(ZoneId.of("UTC")));
        deletion.setExecuteAfter(deletion.getRequestedAt().plusDays(GRACE_DAYS));
        deletion.setCodeHash(sha256(confirmCode));
        deletionMapper.insert(deletion);
        log.warn("用户申请注销 userId={}，宽限期至 {}", userId, deletion.getExecuteAfter());
        return deletion;
    }

    @Override
    @Transactional
    public WishAccountDeletion cancel(Long userId) {
        final WishAccountDeletion existing = getByUser(userId);
        if (existing == null || "CANCELED".equals(existing.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "没有待执行的注销申请");
        }
        if ("EXECUTED".equals(existing.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_DELETION_EXECUTED, "已执行注销，不可撤回");
        }
        existing.setStatus("CANCELED");
        existing.setCanceledAt(LocalDateTime.now(ZoneId.of("UTC")));
        deletionMapper.updateById(existing);
        log.info("用户撤回注销 userId={}", userId);
        return existing;
    }

    @Override
    public WishAccountDeletion getStatus(Long userId) {
        return getByUser(userId);
    }

    @Override
    @Transactional
    public int executeDue() {
        final List<WishAccountDeletion> due = deletionMapper.selectList(
                new LambdaQueryWrapper<WishAccountDeletion>()
                        .eq(WishAccountDeletion::getStatus, "PENDING")
                        .le(WishAccountDeletion::getExecuteAfter, LocalDateTime.now(ZoneId.of("UTC"))));
        int executed = 0;
        for (final WishAccountDeletion task : due) {
            try {
                // 心愿逻辑删除（保留审计；含 PRIVATE/TREE_HOLE 全量）
                wishMapper.delete(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, task.getUserId()));
                task.setStatus("EXECUTED");
                task.setExecutedAt(LocalDateTime.now(ZoneId.of("UTC")));
                deletionMapper.updateById(task);
                executed++;
                log.warn("注销宽限期到期，已执行心愿数据清理 userId={}", task.getUserId());
            } catch (Exception ex) {
                log.error("注销执行失败 userId={}", task.getUserId(), ex);
            }
        }
        return executed;
    }

    private WishAccountDeletion getByUser(Long userId) {
        return deletionMapper.selectOne(new LambdaQueryWrapper<WishAccountDeletion>()
                .eq(WishAccountDeletion::getUserId, userId)
                .orderByDesc(WishAccountDeletion::getId)
                .last("LIMIT 1"));
    }

    private String sha256(String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
