package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminGiftRequest;
import com.cloudmart.wish.dto.SendGiftRequest;
import com.cloudmart.wish.entity.Gift;
import com.cloudmart.wish.entity.GiftRecord;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.GiftTargetType;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.feign.LiveFeignClient;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.GiftMapper;
import com.cloudmart.wish.repository.GiftRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.GiftService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.GiftRecordPageVO;
import com.cloudmart.wish.vo.GiftRecordVO;
import com.cloudmart.wish.vo.GiftSummaryVO;
import com.cloudmart.wish.vo.GiftVO;
import com.cloudmart.wish.vo.SendGiftResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全站虚拟礼物服务实现（V37 迁移）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>送礼事务体由 {@link TransactionTemplate} 驱动：记录落库与星光扣减同事务
 *       （余额不足整体回滚，与文档 6.4「流水与余额更新同事务」一致）</li>
 *   <li>收礼人解析在事务外执行（Feign 网络调用不进事务，避免慢调用持锁）；
 *       目标不存在时拒绝扣费</li>
 *   <li>礼物信息快照化入记录：目录改名/改价/软删不影响历史凭证</li>
 *   <li>直播礼物特效广播为事务提交后增强动作（Fail-Silent），不影响送礼结果</li>
 *   <li>昵称补全走 mall-user 批量接口（避免 N+1），失败回退展示"心愿旅人"</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GiftServiceImpl implements GiftService {

    private static final String STATUS_ON_SHELF = "ON_SHELF";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final GiftMapper giftMapper;
    private final GiftRecordMapper giftRecordMapper;
    private final WishMapper wishMapper;
    private final UserStatService userStatService;
    private final GiftRateLimiter giftRateLimiter;
    private final CommunityFeignClient communityFeignClient;
    private final LiveFeignClient liveFeignClient;
    private final UserFeignClient userFeignClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<GiftVO> listOnShelfGifts() {
        return giftMapper.selectList(new LambdaQueryWrapper<Gift>()
                        .eq(Gift::getStatus, STATUS_ON_SHELF)
                        .orderByAsc(Gift::getSort)
                        .orderByAsc(Gift::getId))
                .stream()
                .map(GiftServiceImpl::toVO)
                .toList();
    }

    @Override
    public SendGiftResultVO sendGift(Long userId, SendGiftRequest request) {
        GiftTargetType targetType = parseTargetType(request.targetType());

        // ---- 前置校验（事务外：目录读 + Feign 收礼人解析，无锁持有）----
        Gift gift = giftMapper.selectById(request.giftId());
        if (gift == null) {
            throw new BusinessException(WishErrorCodes.GIFT_NOT_FOUND, "礼物不存在");
        }
        if (!STATUS_ON_SHELF.equals(gift.getStatus())) {
            throw new BusinessException(WishErrorCodes.GIFT_OFF_SHELF, "该礼物已下架");
        }
        Long receiverId = resolveReceiverId(targetType, request.targetId());

        if (!giftRateLimiter.checkSendDailyLimit(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "今日送礼次数已达上限");
        }

        int totalPrice = Math.multiplyExact(gift.getPriceStarlight(), request.count());

        // ---- 事务体：记录落库（快照）+ 星光扣减（同事务，文档 6.4）----
        GiftRecord record = new GiftRecord();
        record.setSenderId(userId);
        record.setReceiverId(receiverId);
        record.setGiftId(gift.getId());
        record.setGiftName(gift.getName());
        record.setGiftIconUrl(gift.getIconUrl());
        record.setUnitPrice(gift.getPriceStarlight());
        record.setCount(request.count());
        record.setTotalPrice(totalPrice);
        record.setTargetType(targetType.name());
        record.setTargetId(request.targetId());
        record.setMessage(request.message());

        Integer balanceAfter = transactionTemplate.execute(status -> {
            giftRecordMapper.insert(record);
            return userStatService.spendStarlight(userId, totalPrice,
                    ResourceLogSource.GIFT_SEND, record.getId());
        });

        // ---- 事务提交后：直播场景礼物特效广播（Fail-Silent 增强动作）----
        if (targetType == GiftTargetType.LIVE_ROOM) {
            broadcastGiftNoticeAfterCommit(request.targetId(), userId, receiverId, record);
        }

        log.info("送礼成功: userId={}, giftId={}, count={}, total={}, targetType={}, targetId={}, recordId={}",
                userId, gift.getId(), request.count(), totalPrice, targetType, request.targetId(), record.getId());
        return new SendGiftResultVO(record.getId(), gift.getId(), gift.getName(), gift.getIconUrl(),
                request.count(), totalPrice, balanceAfter, receiverId, targetType.name(), request.targetId());
    }

    @Override
    public GiftRecordPageVO listSentRecords(Long userId, Long cursor, Integer pageSize) {
        List<GiftRecord> records = giftRecordMapper.selectList(new LambdaQueryWrapper<GiftRecord>()
                .eq(GiftRecord::getSenderId, userId)
                .lt(cursor != null, GiftRecord::getId, cursor)
                .orderByDesc(GiftRecord::getId)
                .last("LIMIT " + normalizePageSize(pageSize)));
        return toPageVO(records, pageSize);
    }

    @Override
    public GiftRecordPageVO listReceivedRecords(Long userId, Long cursor, Integer pageSize) {
        List<GiftRecord> records = giftRecordMapper.selectList(new LambdaQueryWrapper<GiftRecord>()
                .eq(GiftRecord::getReceiverId, userId)
                .lt(cursor != null, GiftRecord::getId, cursor)
                .orderByDesc(GiftRecord::getId)
                .last("LIMIT " + normalizePageSize(pageSize)));
        return toPageVO(records, pageSize);
    }

    @Override
    public GiftRecordPageVO listTargetRecords(String targetType, Long targetId, Long cursor, Integer pageSize) {
        GiftTargetType type = parseTargetType(targetType);
        List<GiftRecord> records = giftRecordMapper.selectList(new LambdaQueryWrapper<GiftRecord>()
                .eq(GiftRecord::getTargetType, type.name())
                .eq(GiftRecord::getTargetId, targetId)
                .lt(cursor != null, GiftRecord::getId, cursor)
                .orderByDesc(GiftRecord::getId)
                .last("LIMIT " + normalizePageSize(pageSize)));
        return toPageVO(records, pageSize);
    }

    @Override
    public GiftSummaryVO getMyGiftSummary(Long userId) {
        long[] sent = aggregateRecords("sender_id", userId);
        long[] received = aggregateRecords("receiver_id", userId);
        return new GiftSummaryVO(sent[0], sent[1], received[0], received[1]);
    }

    /**
     * 按方向聚合送礼记录（件数 = Σ count，星光 = Σ total_price）。
     * 列名固定为本模块建表列（snake_case），无外部输入拼接面。
     */
    private long[] aggregateRecords(String directionColumn, Long userId) {
        List<Map<String, Object>> rows = giftRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GiftRecord>()
                        .select("COALESCE(SUM(count), 0) AS total_count",
                                "COALESCE(SUM(total_price), 0) AS total_price")
                        .eq(directionColumn, userId));
        if (rows.isEmpty() || rows.get(0) == null) {
            return new long[]{0L, 0L};
        }
        Map<String, Object> row = rows.get(0);
        return new long[]{
                asLong(row.get("total_count")),
                asLong(row.get("total_price"))
        };
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 记录列表 → cursor 分页 VO：游标为本页末条 ID，hasMore 按满页推断 */
    private GiftRecordPageVO toPageVO(List<GiftRecord> records, Integer pageSize) {
        int size = normalizePageSize(pageSize);
        List<GiftRecordVO> vos = toVOsWithUserInfo(records);
        String nextCursor = records.size() == size && !records.isEmpty()
                ? String.valueOf(records.get(records.size() - 1).getId())
                : null;
        return new GiftRecordPageVO(vos, size, nextCursor, records.size() == size);
    }

    // ==================== 管理端 ====================

    @Override
    public List<GiftVO> adminListGifts() {
        return giftMapper.selectList(new LambdaQueryWrapper<Gift>()
                        .orderByAsc(Gift::getSort)
                        .orderByAsc(Gift::getId))
                .stream()
                .map(GiftServiceImpl::toVO)
                .toList();
    }

    @Override
    public GiftVO adminCreateGift(AdminGiftRequest request) {
        Gift gift = new Gift();
        applyRequest(gift, request);
        giftMapper.insert(gift);
        log.info("管理端新增礼物: id={}, name={}, price={}", gift.getId(), gift.getName(), gift.getPriceStarlight());
        return toVO(gift);
    }

    @Override
    public GiftVO adminUpdateGift(Long giftId, AdminGiftRequest request) {
        Gift gift = requireGift(giftId);
        applyRequest(gift, request);
        giftMapper.updateById(gift);
        return toVO(gift);
    }

    @Override
    public GiftVO adminUpdateGiftStatus(Long giftId, boolean onShelf) {
        Gift gift = requireGift(giftId);
        gift.setStatus(onShelf ? STATUS_ON_SHELF : "OFF_SHELF");
        giftMapper.updateById(gift);
        return toVO(gift);
    }

    @Override
    public void adminDeleteGift(Long giftId) {
        requireGift(giftId);
        // 软删（@TableLogic）：历史送礼记录仍持有快照，目录行保留审计轨迹
        giftMapper.deleteById(giftId);
        log.info("管理端删除礼物: id={}", giftId);
    }

    @Override
    public List<GiftRecordVO> adminListRecords(Long senderId, Long receiverId,
                                               String targetType, Long targetId,
                                               Integer page, Integer pageSize) {
        GiftTargetType type = targetType == null ? null : parseTargetType(targetType);
        int size = Math.min(pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize, 100);
        int pageIndex = page == null || page < 1 ? 1 : page;
        List<GiftRecord> records = giftRecordMapper.selectList(new LambdaQueryWrapper<GiftRecord>()
                .eq(senderId != null, GiftRecord::getSenderId, senderId)
                .eq(receiverId != null, GiftRecord::getReceiverId, receiverId)
                .eq(type != null, GiftRecord::getTargetType, type == null ? null : type.name())
                .eq(targetId != null, GiftRecord::getTargetId, targetId)
                .orderByDesc(GiftRecord::getId)
                .last("LIMIT " + size + " OFFSET " + (long) (pageIndex - 1) * size));
        return toVOsWithUserInfo(records);
    }

    // ==================== 内部方法 ====================

    /**
     * 解析收礼人（事务外）：心愿作者 / 帖子作者 / 直播间主播。
     * 目标归属查询失败（服务不可用）时抛 503，拒绝扣费。
     */
    private Long resolveReceiverId(GiftTargetType targetType, Long targetId) {
        return switch (targetType) {
            case WISH -> {
                Wish wish = wishMapper.selectById(targetId);
                yield wish != null ? wish.getUserId()
                        : targetNotFound(targetType, targetId);
            }
            case POST -> {
                Map<String, Object> owner = requireFeignData(
                        communityFeignClient.getPostOwner(targetId),
                        "COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
                yield owner != null && owner.get("userId") instanceof Number number
                        ? number.longValue()
                        : targetNotFound(targetType, targetId);
            }
            case LIVE_ROOM -> {
                Map<String, Object> owner = requireFeignData(
                        liveFeignClient.getRoomOwner(targetId),
                        "LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
                yield owner != null && owner.get("ownerId") instanceof Number number
                        ? number.longValue()
                        : targetNotFound(targetType, targetId);
            }
        };
    }

    /** 目标不存在统一入口（404） */
    private Long targetNotFound(GiftTargetType targetType, Long targetId) {
        throw new BusinessException(WishErrorCodes.GIFT_TARGET_NOT_FOUND,
                "送礼对象不存在: " + targetType.name() + "#" + targetId);
    }

    /** Feign 降级信封（服务不可用）转为 503，避免误判为"目标不存在" */
    private Map<String, Object> requireFeignData(ApiResponse<Map<String, Object>> response,
                                                 String code, String message) {
        if (response == null || !response.success()) {
            throw new BusinessException(code, message);
        }
        return response.data();
    }

    /** 直播间礼物特效广播：事务提交后执行；失败仅记日志（增强动作，不影响送礼结果） */
    private void broadcastGiftNoticeAfterCommit(Long roomId, Long senderId, Long receiverId, GiftRecord record) {
        Runnable publish = () -> {
            try {
                liveFeignClient.broadcastGiftNotice(roomId, Map.of(
                        "senderId", senderId,
                        "receiverId", receiverId,
                        "giftId", record.getGiftId(),
                        "giftName", record.getGiftName(),
                        "giftIconUrl", record.getGiftIconUrl() == null ? "" : record.getGiftIconUrl(),
                        "count", record.getCount(),
                        "message", record.getMessage() == null ? "" : record.getMessage()));
            } catch (Exception e) {
                log.warn("直播间礼物广播失败(降级), roomId={}, recordId={}", roomId, record.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private GiftTargetType parseTargetType(String value) {
        try {
            return GiftTargetType.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(WishErrorCodes.GIFT_TARGET_TYPE_INVALID, "送礼场景非法: " + value);
        }
    }

    private Gift requireGift(Long giftId) {
        Gift gift = giftMapper.selectById(giftId);
        if (gift == null) {
            throw new BusinessException(WishErrorCodes.GIFT_NOT_FOUND, "礼物不存在");
        }
        return gift;
    }

    private void applyRequest(Gift gift, AdminGiftRequest request) {
        gift.setName(request.name());
        gift.setIconUrl(request.iconUrl());
        gift.setAnimationUrl(request.animationUrl());
        gift.setPriceStarlight(request.priceStarlight());
        gift.setStatus(request.status() == null ? "OFF_SHELF" : request.status());
        gift.setSort(request.sort() == null ? 0 : request.sort());
        gift.setDescription(request.description());
    }

    /** 记录列表补全送礼人/收礼人昵称（批量查询，失败回退默认昵称，Fail-Open） */
    private List<GiftRecordVO> toVOsWithUserInfo(List<GiftRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = records.stream()
                .flatMap(record -> java.util.stream.Stream.of(record.getSenderId(), record.getReceiverId()))
                .collect(Collectors.toSet());
        Map<Long, Map<String, Object>> userMap = fetchUserInfoMap(userIds);
        return records.stream()
                .map(record -> new GiftRecordVO(
                        record.getId(),
                        record.getGiftId(),
                        record.getGiftName(),
                        record.getGiftIconUrl(),
                        record.getCount(),
                        record.getTotalPrice(),
                        record.getSenderId(),
                        nicknameOf(userMap, record.getSenderId()),
                        record.getReceiverId(),
                        nicknameOf(userMap, record.getReceiverId()),
                        record.getTargetType(),
                        record.getTargetId(),
                        record.getMessage(),
                        record.getCreatedAt()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, Object>> fetchUserInfoMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<Map<String, Object>>> response =
                    userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response == null || !response.success() || response.data() == null) {
                return Map.of();
            }
            return response.data().stream()
                    .filter(user -> user.get("id") instanceof Number)
                    .collect(Collectors.toMap(
                            user -> ((Number) user.get("id")).longValue(),
                            Function.identity(),
                            (first, second) -> first));
        } catch (Exception e) {
            log.warn("送礼记录用户信息补全失败(降级): {}", e.getMessage());
            return Map.of();
        }
    }

    private String nicknameOf(Map<Long, Map<String, Object>> userMap, Long userId) {
        Map<String, Object> user = userMap.get(userId);
        return user != null
                ? (String) user.getOrDefault("nickname", "心愿旅人")
                : "心愿旅人";
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static GiftVO toVO(Gift gift) {
        return new GiftVO(gift.getId(), gift.getName(), gift.getIconUrl(), gift.getAnimationUrl(),
                gift.getPriceStarlight(), gift.getStatus(), gift.getSort(), gift.getDescription());
    }
}
