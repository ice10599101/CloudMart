package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.StarlightTransfer;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.repository.StarlightTransferMapper;
import com.cloudmart.wish.service.StarlightTransferService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.StarlightTransferVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 星光转赠（§6）。
 *
 * <p>风控口径：非自转；好友=任一关注方向（社区服务校验，不可用 fail-closed 拒绝——
 * 转赠是资产转移，宁拒不误放）；单笔 10..100、每日累计 ≤200（转出方，服务器本地日）；
 * spend/earn 同事务（同一 mall-wish 库，原子成立；不足回滚整体）。
 * 流水先插（获取 refId）后对转，任一失败整体回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StarlightTransferServiceImpl implements StarlightTransferService {

    private static final int MIN_AMOUNT = 10;
    private static final int MAX_AMOUNT = 100;
    private static final int DAILY_CAP = 200;
    private static final int MAX_MESSAGE = 100;

    private final StarlightTransferMapper transferMapper;
    private final UserStatService userStatService;
    private final CommunityFeignClient communityFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarlightTransferVO transfer(Long fromUserId, Long toUserId, int amount, String message) {
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不能转赠给自己");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "单笔转赠需在 " + MIN_AMOUNT + ".." + MAX_AMOUNT + " 星光之间");
        }
        String trimmedMessage = message == null ? null : message.trim();
        if (trimmedMessage != null && trimmedMessage.length() > MAX_MESSAGE) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "附言最长 " + MAX_MESSAGE + " 字");
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

        // 日累计限额（转出方，服务器本地日，与签到/经验日界口径一致）
        LocalDate today = LocalDate.now();
        Integer usedToday = transferMapper.selectList(new LambdaQueryWrapper<StarlightTransfer>()
                        .eq(StarlightTransfer::getFromUserId, fromUserId)
                        .ge(StarlightTransfer::getCreatedAt, today.atStartOfDay()))
                .stream().mapToInt(t -> t.getAmount() == null ? 0 : t.getAmount()).sum();
        if (usedToday + amount > DAILY_CAP) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED,
                    "今日转赠已达上限（累计 " + DAILY_CAP + " 星光/天）");
        }

        // 流水先行（获取 refId），spend/earn 对转同事务：不足或异常整体回滚
        StarlightTransfer transfer = new StarlightTransfer();
        transfer.setFromUserId(fromUserId);
        transfer.setToUserId(toUserId);
        transfer.setAmount(amount);
        transfer.setMessage(trimmedMessage);
        transferMapper.insert(transfer);

        userStatService.spendStarlight(fromUserId, amount, ResourceLogSource.TRANSFER_OUT, transfer.getId());
        userStatService.earnStarlight(toUserId, amount, ResourceLogSource.TRANSFER_IN, transfer.getId());

        log.info("星光转赠成功: from={}, to={}, amount={}, transferId={}", fromUserId, toUserId, amount, transfer.getId());
        return new StarlightTransferVO(transfer.getId(), fromUserId, toUserId, amount, trimmedMessage, "SENT", transfer.getCreatedAt());
    }

    @Override
    public List<StarlightTransferVO> myTransfers(Long userId) {
        List<StarlightTransfer> rows = transferMapper.selectList(
                new LambdaQueryWrapper<StarlightTransfer>()
                        .and(w -> w.eq(StarlightTransfer::getFromUserId, userId)
                                .or().eq(StarlightTransfer::getToUserId, userId))
                        .orderByDesc(StarlightTransfer::getId)
                        .last("LIMIT 20"));
        return rows.stream()
                .map(t -> new StarlightTransferVO(t.getId(), t.getFromUserId(), t.getToUserId(),
                        t.getAmount() == null ? 0 : t.getAmount(), t.getMessage(),
                        userId.equals(t.getFromUserId()) ? "SENT" : "RECEIVED", t.getCreatedAt()))
                .toList();
    }
}
