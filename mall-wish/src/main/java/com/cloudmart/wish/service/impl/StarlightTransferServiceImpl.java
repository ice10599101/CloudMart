package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.StarlightDailyOutQuota;
import com.cloudmart.wish.entity.StarlightTransfer;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.mq.StarlightNotifyProducer;
import com.cloudmart.wish.repository.StarlightDailyOutQuotaMapper;
import com.cloudmart.wish.repository.StarlightTransferMapper;
import com.cloudmart.wish.service.StarlightTransferService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.StarlightTransferVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 星光转赠（§6/R06）：守恒、原子限额、持久幂等。
 *
 * <p>守恒（R06）：双方钱包按 userId 升序锁定，转出/转入等额同事务，接收余量不足
 * <b>整笔拒绝</b>——绝不"扣 100 到账 5"静默销毁差额；SETTLED 要求 amount=creditedAmount，
 * 双边流水等额异号可对账。</p>
 *
 * <p>限额（R06）：日转出额度权威行 (user_id, business_date) 条件更新原子占用，
 * 替换"先汇总再写"的竞态口径；business_date 为服务端 Asia/Shanghai 业务日。</p>
 *
 * <p>幂等（R06）：uk(from_user_id, request_key) 持久化——Redis 过滤器故障放行或过期后
 * 同键重放仍返回原结果；同键不同载荷 409。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StarlightTransferServiceImpl implements StarlightTransferService {

    private static final int MIN_AMOUNT = 10;
    private static final int MAX_AMOUNT = 100;
    private static final int DAILY_CAP = 200;
    private static final int MAX_MESSAGE = 100;
    /** R22：经济奖励/日限额统一服务端业务日（Asia/Shanghai 零点重置） */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final StarlightTransferMapper transferMapper;
    private final StarlightDailyOutQuotaMapper quotaMapper;
    private final UserStatService userStatService;
    private final CommunityFeignClient communityFeignClient;
    private final StarlightNotifyProducer notifyProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarlightTransferVO transfer(Long fromUserId, Long toUserId, int amount,
                                        String message, String requestKey) {
        if (toUserId == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "转入用户不能为空");
        }
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不能转赠给自己");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "单笔转赠需在 " + MIN_AMOUNT + ".." + MAX_AMOUNT + " 星光之间");
        }
        String trimmedMessage = message == null || message.isBlank() ? null : message.trim();
        if (trimmedMessage != null && trimmedMessage.length() > MAX_MESSAGE) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "附言最长 " + MAX_MESSAGE + " 字");
        }
        String key = requestKey == null || requestKey.isBlank() ? null : requestKey.trim();
        if (key != null) {
            if (key.length() > 64) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "请求键过长");
            }
            // 持久幂等：同键重放返回原结果；同键异载荷 409（防重放篡改）
            StarlightTransfer replayed = transferMapper.selectOne(
                    new LambdaQueryWrapper<StarlightTransfer>()
                            .eq(StarlightTransfer::getFromUserId, fromUserId)
                            .eq(StarlightTransfer::getRequestKey, key)
                            .last("LIMIT 1"));
            if (replayed != null) {
                if (!replayed.getToUserId().equals(toUserId) || replayed.getAmount() != amount) {
                    throw new BusinessException(WishErrorCodes.WISH_TRANSFER_REQUEST_CONFLICT,
                            "请求键已被不同转赠使用");
                }
                return toVo(replayed, fromUserId, null);
            }
        }

        // 好友校验：社区服务不可用 fail-closed（转赠是资产转移，宁拒不误放）
        boolean friends = false;
        try {
            ApiResponse<Map<String, Object>> res = communityFeignClient.checkFriendship(fromUserId, toUserId);
            friends = res != null && res.data() != null && Boolean.TRUE.equals(res.data().get("friends"));
        } catch (Exception e) {
            log.warn("好友关系校验异常（fail-closed 拒绝）: from={}, to={}", fromUserId, toUserId, e);
        }
        if (!friends) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "仅可转赠给社区好友（关注关系）");
        }

        LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
        // 日额度原子占用：INSERT IGNORE 建当日行 + 条件更新 used_amount+amount<=200（同事务）
        occupyDailyQuota(fromUserId, businessDate, amount);

        // 转赠事实先行（获取 refId）；等额对转 + 额度占用 + 事实同事务，任一失败整体回滚
        StarlightTransfer transfer = new StarlightTransfer();
        transfer.setFromUserId(fromUserId);
        transfer.setToUserId(toUserId);
        transfer.setAmount(amount);
        transfer.setRequestKey(key);
        transfer.setStatus(StarlightTransfer.STATUS_SETTLED);
        transfer.setBusinessDate(businessDate);
        transfer.setCreditedAmount(amount);
        transfer.setMessage(trimmedMessage);
        transferMapper.insert(transfer);

        // R06：等额对转（升序锁双钱包，接收余量不足抛 RECEIVER_CAPACITY 整笔回滚）
        UserStatService.StarlightPairTransfer pair = userStatService.transferStarlightPair(
                fromUserId, toUserId, amount, transfer.getId());
        transfer.setSenderLedgerId(pair.senderLedgerId());
        transfer.setReceiverLedgerId(pair.receiverLedgerId());
        transferMapper.updateById(transfer);

        log.info("星光转赠成功（守恒）: from={}, to={}, amount={}, transferId={}",
                fromUserId, toUserId, amount, transfer.getId());
        // R19：到账通知在事务提交后发出（回滚不通知；Fail-Open 不影响资产事实）。
        // isSynchronizationActive 守卫：本方法 @Transactional，生产必有同步；直接单测无事务时跳过
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final Long transferId = transfer.getId();
            final String settledMessage = trimmedMessage;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyProducer.publishTransferReceived(toUserId, transferId, amount, settledMessage);
                }
            });
        }
        TransferLimits limits = transferLimits(fromUserId);
        return toVo(transfer, fromUserId, limits.remainingToday());
    }

    @Override
    public TransferPage myTransfers(Long userId, int page, int pageSize) {
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        Page<StarlightTransfer> result = transferMapper.selectPage(new Page<>(Math.max(page, 1), safeSize),
                new LambdaQueryWrapper<StarlightTransfer>()
                        .and(w -> w.eq(StarlightTransfer::getFromUserId, userId)
                                .or().eq(StarlightTransfer::getToUserId, userId))
                        .orderByDesc(StarlightTransfer::getId));
        List<StarlightTransferVO> records = result.getRecords().stream()
                .map(t -> toVo(t, userId, null))
                .toList();
        return new TransferPage(records, result.getTotal());
    }

    @Override
    public TransferLimits transferLimits(Long userId) {
        LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
        StarlightDailyOutQuota quota = quotaMapper.selectOne(
                new LambdaQueryWrapper<StarlightDailyOutQuota>()
                        .eq(StarlightDailyOutQuota::getUserId, userId)
                        .eq(StarlightDailyOutQuota::getBusinessDate, businessDate)
                        .last("LIMIT 1"));
        int used = quota == null || quota.getUsedAmount() == null ? 0 : quota.getUsedAmount();
        return new TransferLimits(DAILY_CAP, used, Math.max(DAILY_CAP - used, 0), businessDate);
    }

    // ---------------- 内部实现 ----------------

    /** 日额度原子占用：条件更新 0 行即超限，抛 WISH_RATE_LIMITED 整体回滚 */
    private void occupyDailyQuota(Long userId, LocalDate businessDate, int amount) {
        StarlightDailyOutQuota quota = new StarlightDailyOutQuota();
        quota.setUserId(userId);
        quota.setBusinessDate(businessDate);
        quota.setUsedAmount(0);
        quota.setVersion(0);
        try {
            quotaMapper.insert(quota);
        } catch (DuplicateKeyException alreadyExists) {
            // 当日行已存在（并发首转），走条件更新
        }
        int updated = quotaMapper.update(null, new LambdaUpdateWrapper<StarlightDailyOutQuota>()
                .setSql("used_amount = used_amount + {0}", amount)
                .setSql("version = version + 1")
                .eq(StarlightDailyOutQuota::getUserId, userId)
                .eq(StarlightDailyOutQuota::getBusinessDate, businessDate)
                .apply("used_amount + {0} <= {1}", amount, DAILY_CAP));
        if (updated == 0) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED,
                    "今日转赠已达上限（累计 " + DAILY_CAP + " 星光/天）");
        }
    }

    private StarlightTransferVO toVo(StarlightTransfer t, Long viewerId, Integer remainingToday) {
        return new StarlightTransferVO(t.getId(), t.getFromUserId(), t.getToUserId(),
                t.getAmount() == null ? 0 : t.getAmount(), t.getMessage(),
                Objects.equals(t.getFromUserId(), viewerId) ? "SENT" : "RECEIVED",
                t.getCreatedAt(),
                t.getStatus(), remainingToday);
    }
}
