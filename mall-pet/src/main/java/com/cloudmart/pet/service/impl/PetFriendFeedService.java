package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.entity.PetFriend;
import com.cloudmart.pet.entity.PetFriendFeed;
import com.cloudmart.pet.entity.PetFriendFeedCursor;
import com.cloudmart.pet.enums.PetFriendStatus;
import com.cloudmart.pet.repository.PetFriendFeedCursorMapper;
import com.cloudmart.pet.repository.PetFriendFeedMapper;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友动态 Feed 服务（F3）：收件箱扇出写 + 游标分页 + 已读水位。
 *
 * <p>扇出策略：事件发生时查 actor 的 ACTIVE 好友行（双向各一行，取 friendUserId），
 * 逐好友写一条收件箱行（与业务同事务，好友量 ≤100 可接受）；无好友或写失败仅 WARN
 * （动态为展示型增强，不阻断主业务）。</p>
 *
 * <p>已读水位：cursor.user_id 主键 upsert；未读数 = 水位之后条数（LIMIT 100 截断计数）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetFriendFeedService {

    /** 单收件人扇出上限（好友数软上限，超过截断并 WARN——防异常账号扇出风暴） */
    private static final int MAX_FANOUT = 100;

    /** 事件类型（与调用方埋点一一对应） */
    public static final String EVENT_LEVEL_UP = "LEVEL_UP";
    public static final String EVENT_WORK_COMPLETED = "WORK_COMPLETED";
    public static final String EVENT_STUDY_COMPLETED = "STUDY_COMPLETED";
    public static final String EVENT_BATTLE_WIN = "BATTLE_WIN";

    private final PetFriendMapper friendMapper;
    private final PetFriendFeedMapper feedMapper;
    private final PetFriendFeedCursorMapper cursorMapper;
    /** R21：屏蔽贯通——已互相拉黑的好友不再接收新动态扇出，读取时过滤历史行 */
    private final com.cloudmart.pet.service.PetUserBlockService blockService;

    /**
     * 扇出写一条动态给 actor 的全部好友（调用方事务内执行）。
     *
     * @param actorUserId 动态主体用户
     * @param actorPetId  动态主体宠物（可空）
     * @param eventType   事件类型（EVENT_* 常量）
     * @param text        展示文案（已按宠物口吻生成，如"小橘 升到了 Lv.5！"）
     * @param petName     主体宠物名（前端展示用）
     */
    public void append(Long actorUserId, Long actorPetId, String eventType, String text, String petName) {
        try {
            List<Long> friendIds = friendMapper.selectList(new LambdaQueryWrapper<PetFriend>()
                            .eq(PetFriend::getUserId, actorUserId)
                            .eq(PetFriend::getStatus, PetFriendStatus.ACTIVE.name())
                            .select(PetFriend::getFriendUserId)
                            .last("LIMIT " + (MAX_FANOUT + 1)))
                    .stream().map(PetFriend::getFriendUserId)
                    // R21：拉黑后不再扇出新动态（历史行为存储优化另行异步清理）
                    .filter(friendId -> !blockService.isBlockedEitherWay(actorUserId, friendId))
                    .toList();
            if (friendIds.size() > MAX_FANOUT) {
                log.warn("好友数超过扇出上限，截断: actor={}, count>{}", actorUserId, MAX_FANOUT);
                friendIds = friendIds.subList(0, MAX_FANOUT);
            }
            if (friendIds.isEmpty()) {
                return;
            }
            Map<String, String> payload = new HashMap<>();
            payload.put("text", text);
            if (petName != null) {
                payload.put("petName", petName);
            }
            String payloadJson = PetJsonUtils.toJson(payload);
            for (Long friendId : friendIds) {
                PetFriendFeed feed = new PetFriendFeed();
                feed.setUserId(friendId);
                feed.setActorUserId(actorUserId);
                feed.setActorPetId(actorPetId);
                feed.setEventType(eventType);
                feed.setPayloadJson(payloadJson);
                feedMapper.insert(feed);
            }
        } catch (Exception e) {
            // 动态为展示型增强：失败不阻断主业务（与提醒 Fail-Open 同风格）
            log.warn("好友动态扇出失败（不阻断）: actor={}, eventType={}", actorUserId, eventType, e);
        }
    }

    /** 动态条目 VO */
    public record FeedItemVO(Long feedId, Long actorUserId, Long actorPetId, String eventType,
                             String text, String petName, String createdAt) {
    }

    /** 收件箱游标分页（id 倒序；beforeId 为上一页最后一条） */
    public List<FeedItemVO> list(Long userId, Long beforeId, int size) {
        int safeSize = Math.min(Math.max(1, size), 50);
        LambdaQueryWrapper<PetFriendFeed> wrapper = new LambdaQueryWrapper<PetFriendFeed>()
                .eq(PetFriendFeed::getUserId, userId)
                .orderByDesc(PetFriendFeed::getId)
                .last("LIMIT " + safeSize);
        if (beforeId != null) {
            wrapper.lt(PetFriendFeed::getId, beforeId);
        }
        // R21：读取时复验屏蔽——取消好友/拉黑后历史动态即时不可见（异步清理只是存储优化）
        List<PetFriendFeed> rows = feedMapper.selectList(wrapper);
        List<Long> blockedActors = rows.stream().map(PetFriendFeed::getActorUserId).distinct()
                .filter(actorId -> blockService.isBlockedEitherWay(userId, actorId))
                .toList();
        return rows.stream()
                .filter(row -> !blockedActors.contains(row.getActorUserId()))
                .map(this::toVo)
                .toList();
    }

    /** 未读数（水位之后；100 封顶展示"99+"由前端处理） */
    public long unreadCount(Long userId) {
        Long watermark = lastReadId(userId);
        return feedMapper.selectCount(new LambdaQueryWrapper<PetFriendFeed>()
                .eq(PetFriendFeed::getUserId, userId)
                .gt(watermark != null && watermark > 0, PetFriendFeed::getId, watermark));
    }

    /** 已读推进：水位 upsert 到当前最大 id（幂等，只前进不后退） */
    public void markRead(Long userId) {
        Long maxId = feedMapper.selectList(new LambdaQueryWrapper<PetFriendFeed>()
                        .eq(PetFriendFeed::getUserId, userId)
                        .orderByDesc(PetFriendFeed::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().map(PetFriendFeed::getId).orElse(null);
        if (maxId == null) {
            return;
        }
        int updated = cursorMapper.update(null, new LambdaUpdateWrapper<PetFriendFeedCursor>()
                .setSql("last_read_id = GREATEST(last_read_id, {0})", maxId)
                .eq(PetFriendFeedCursor::getUserId, userId));
        if (updated == 0) {
            PetFriendFeedCursor cursor = new PetFriendFeedCursor();
            cursor.setUserId(userId);
            cursor.setLastReadId(maxId);
            try {
                cursorMapper.insert(cursor);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发推进：幂等
            }
        }
    }

    private Long lastReadId(Long userId) {
        PetFriendFeedCursor cursor = cursorMapper.selectById(userId);
        return cursor != null ? cursor.getLastReadId() : null;
    }

    private FeedItemVO toVo(PetFriendFeed feed) {
        Map<String, Object> payload = PetJsonUtils.parse(feed.getPayloadJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String text = payload != null && payload.get("text") != null
                ? String.valueOf(payload.get("text")) : "";
        String petName = payload != null && payload.get("petName") != null
                ? String.valueOf(payload.get("petName")) : null;
        return new FeedItemVO(feed.getId(), feed.getActorUserId(), feed.getActorPetId(),
                feed.getEventType(), text, petName,
                // R20：DB 存 UTC——toString() 无时区会被三端当本地时间解析，统一 RFC3339 带 Z
                feed.getCreatedAt() != null
                        ? feed.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
    }
}
