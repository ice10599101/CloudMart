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

    /**
     * B20：发码结果真实回传——通道未接入时 sent=false + 明确提示，不再让控制器
     * 固定 sent=true 假成功。echo-code 仅限开发/测试回显（生产必须关闭）。
     */
    @Override
    public SendCodeResult sendDeletionCode(Long userId) {
        // 已执行的注销不再发码
        final WishAccountDeletion existing = getByUser(userId);
        if (existing != null && "EXECUTED".equals(existing.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_DELETION_EXECUTED, "账号已注销");
        }
        final String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + userId, sha256(code), CODE_TTL);
        if (echoCode) {
            log.warn("注销验证码回显模式（仅开发/测试）userId={}", userId);
            return new com.cloudmart.wish.service.AccountDeletionService.SendCodeResult(true, code, null);
        }
        // 真实短信/邮件通道尚未接入（mall-notification 仅站内信）——如实返回未发送
        log.warn("注销验证码已生成但无下发通道 userId={}（B20：sent=false，不假成功）", userId);
        return new com.cloudmart.wish.service.AccountDeletionService.SendCodeResult(false, null,
                "验证码下发通道暂未接入，请通过客服人工核验后继续注销流程");
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
        // B20：取消只能 CAS PENDING 且未过截止时间（与到期执行并发时只有一个成功）
        int affected = deletionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishAccountDeletion>()
                        .eq(WishAccountDeletion::getId, existing.getId())
                        .eq(WishAccountDeletion::getStatus, "PENDING")
                        .gt(WishAccountDeletion::getExecuteAfter, LocalDateTime.now(ZoneId.of("UTC")))
                        .set(WishAccountDeletion::getStatus, "CANCELED")
                        .set(WishAccountDeletion::getCanceledAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "注销申请状态已变更（可能已到期执行），请刷新查看");
        }
        log.info("用户撤回注销 userId={}", userId);
        return getByUser(userId);
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
            // B20：PENDING→EXECUTING 认领（多实例/重复调度只有一个执行者）
            int claimed = deletionMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishAccountDeletion>()
                            .eq(WishAccountDeletion::getId, task.getId())
                            .eq(WishAccountDeletion::getStatus, "PENDING")
                            .set(WishAccountDeletion::getStatus, "EXECUTING"));
            if (claimed == 0) {
                continue;
            }
            try {
                // 心愿逻辑删除（保留审计；含 PRIVATE/TREE_HOLE 全量）
                wishMapper.delete(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, task.getUserId()));
                int done = deletionMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishAccountDeletion>()
                                .eq(WishAccountDeletion::getId, task.getId())
                                .eq(WishAccountDeletion::getStatus, "EXECUTING")
                                .set(WishAccountDeletion::getStatus, "EXECUTED")
                                .set(WishAccountDeletion::getExecutedAt, LocalDateTime.now(ZoneId.of("UTC"))));
                if (done == 1) {
                    executed++;
                }
                log.warn("注销宽限期到期，已执行心愿数据清理 userId={}", task.getUserId());
            } catch (Exception ex) {
                // 回退 PENDING，下轮扫描重试（清理幂等：软删重复执行无害）
                deletionMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WishAccountDeletion>()
                                .eq(WishAccountDeletion::getId, task.getId())
                                .eq(WishAccountDeletion::getStatus, "EXECUTING")
                                .set(WishAccountDeletion::getStatus, "PENDING"));
                log.error("注销执行失败 userId={}（已回退待重试）", task.getUserId(), ex);
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
