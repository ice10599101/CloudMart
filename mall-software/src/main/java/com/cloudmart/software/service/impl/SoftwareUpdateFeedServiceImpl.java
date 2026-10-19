package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSubscription;
import com.cloudmart.software.entity.SoftwareUpdateRead;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionDeliveryMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUpdateReadMapper;
import com.cloudmart.software.service.SoftwareUpdateFeedService;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UX05 实现：更新流复用 SF05 投递行（delivery→task→revision 变更摘要），
 * 已读台账唯一键幂等；下架软件的历史更新保留并标注不可用，不泄露内部信息。
 */
@Service
@RequiredArgsConstructor
public class SoftwareUpdateFeedServiceImpl implements SoftwareUpdateFeedService {

    private static final int MAX_READ_BATCH = 50;
    private static final int MAX_PAGE_SIZE = 30;

    private final SoftwareSubscriptionMapper subscriptionMapper;
    private final SoftwareSubscriptionDeliveryMapper deliveryMapper;
    private final SoftwareFanoutTaskMapper fanoutTaskMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareUpdateReadMapper readMapper;
    private final SoftwareMapper softwareMapper;
    private final com.cloudmart.software.service.SoftwareQueryService softwareQueryService;
    private final Clock clock;

    @Override
    public List<Map<String, Object>> mySubscriptions(Long userId) {
        List<SoftwareSubscription> subs = subscriptionMapper.selectList(
                new LambdaQueryWrapper<SoftwareSubscription>()
                        .eq(SoftwareSubscription::getUserId, userId)
                        .orderByDesc(SoftwareSubscription::getCreatedAt));
        if (subs.isEmpty()) {
            return List.of();
        }
        List<Long> softwareIds = subs.stream()
                .map(SoftwareSubscription::getSoftwareId).distinct().toList();
        List<SoftwareCardVO> cards = softwareQueryService.cardsByIds(softwareIds);
        Map<Long, SoftwareCardVO> cardById = new HashMap<>();
        for (SoftwareCardVO card : cards) {
            cardById.put(card.id(), card);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareSubscription sub : subs) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("softwareId", String.valueOf(sub.getSoftwareId()));
            view.put("platforms", sub.getPlatforms());
            view.put("channels", sub.getChannels());
            view.put("subscribedAt", sub.getCreatedAt() == null ? null : sub.getCreatedAt().toString());
            SoftwareCardVO card = cardById.get(sub.getSoftwareId());
            view.put("name", card == null ? null : card.name());
            view.put("iconAssetId", card == null ? null : card.iconAssetId());
            view.put("visible", card != null); // 下架/封禁软件订阅行不泄露，仅标注不可用
            view.put("unreadCount", unreadCountOf(userId, sub.getSoftwareId()));
            result.add(view);
        }
        return result;
    }

    @Override
    public Map<String, Object> myUpdates(Long userId, long cursor, int size) {
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long safeCursor = cursor <= 0 ? Long.MAX_VALUE : cursor;
        var deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSubscriptionDelivery>()
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getUserId, userId)
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getStatus, "SENT")
                        .lt(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId, safeCursor)
                        .orderByDesc(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId)
                        .last("LIMIT " + (safeSize + 1)));
        boolean hasMore = deliveries.size() > safeSize;
        List<com.cloudmart.software.entity.SoftwareSubscriptionDelivery> page =
                hasMore ? deliveries.subList(0, safeSize) : deliveries;

        Set<Long> ids = new HashSet<>();
        for (var d : page) {
            ids.add(d.getId());
        }
        Set<Long> readIds = ids.isEmpty() ? Set.of()
                : readMapper.selectList(new LambdaQueryWrapper<SoftwareUpdateRead>()
                        .eq(SoftwareUpdateRead::getUserId, userId)
                        .in(SoftwareUpdateRead::getDeliveryId, ids))
                .stream().map(SoftwareUpdateRead::getDeliveryId).collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> items = new ArrayList<>();
        long nextCursor = 0;
        for (var delivery : page) {
            nextCursor = delivery.getId();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventId", String.valueOf(delivery.getId()));
            item.put("softwareId", String.valueOf(delivery.getSoftwareId()));
            item.put("read", readIds.contains(delivery.getId()));
            // delivery→task（software+seq 定位）→revision→release 变更摘要
            SoftwareFanoutTask task = fanoutTaskMapper.selectOne(
                    new LambdaQueryWrapper<SoftwareFanoutTask>()
                            .eq(SoftwareFanoutTask::getSoftwareId, delivery.getSoftwareId())
                            .eq(SoftwareFanoutTask::getPublicationSeq, delivery.getPublicationSeq())
                            .last("LIMIT 1"));
            if (task != null) {
                item.put("publishedAt", task.getPublishedAt() == null ? null : task.getPublishedAt().toString());
                item.put("businessChange", Boolean.TRUE.equals(task.getBusinessChange()));
                SoftwareRevision revision = revisionMapper.selectById(task.getRevisionId());
                if (revision != null) {
                    item.put("softwareName", revision.getName());
                    SoftwareRelease release = releaseMapper.selectOne(
                            new LambdaQueryWrapper<SoftwareRelease>()
                                    .eq(SoftwareRelease::getRevisionId, revision.getId())
                                    .orderByAsc(SoftwareRelease::getId)
                                    .last("LIMIT 1"));
                    if (release != null) {
                        item.put("versionName", release.getVersionName());
                        item.put("changelog", release.getChangelog());
                    }
                }
            }
            Software software = softwareMapper.selectById(delivery.getSoftwareId());
            item.put("softwareVisible", software != null
                    && software.getLifecycle() == SoftwareLifecycle.ACTIVE
                    && software.getPublishedRevisionId() != null);
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("hasMore", hasMore);
        result.put("nextCursor", hasMore ? String.valueOf(nextCursor) : null);
        return result;
    }

    @Override
    @Transactional
    public int markRead(Long userId, List<Long> deliveryIds) {
        List<Long> distinct = deliveryIds == null ? List.of()
                : deliveryIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return 0;
        }
        if (distinct.size() > MAX_READ_BATCH) {
            throw new BusinessException("SOFTWARE_UPDATE_INVALID",
                    "单次标记已读最多 " + MAX_READ_BATCH + " 条");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int inserted = 0;
        for (Long deliveryId : distinct) {
            SoftwareUpdateRead existing = readMapper.selectOne(new LambdaQueryWrapper<SoftwareUpdateRead>()
                    .eq(SoftwareUpdateRead::getUserId, userId)
                    .eq(SoftwareUpdateRead::getDeliveryId, deliveryId)
                    .last("LIMIT 1"));
            if (existing != null) {
                continue; // 重复标记幂等
            }
            SoftwareUpdateRead read = new SoftwareUpdateRead();
            read.setUserId(userId);
            read.setDeliveryId(deliveryId);
            read.setReadAt(now);
            readMapper.insert(read);
            inserted++;
        }
        return inserted;
    }

    @Override
    public long unreadCount(Long userId) {
        List<com.cloudmart.software.entity.SoftwareSubscriptionDelivery> sent = deliveryMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSubscriptionDelivery>()
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getUserId, userId)
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getStatus, "SENT")
                        .select(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId));
        if (sent.isEmpty()) {
            return 0;
        }
        List<Long> ids = sent.stream()
                .map(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId).toList();
        Long read = readMapper.selectCount(new LambdaQueryWrapper<SoftwareUpdateRead>()
                .eq(SoftwareUpdateRead::getUserId, userId)
                .in(SoftwareUpdateRead::getDeliveryId, ids));
        return ids.size() - read;
    }

    private long unreadCountOf(Long userId, Long softwareId) {
        List<com.cloudmart.software.entity.SoftwareSubscriptionDelivery> sent = deliveryMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSubscriptionDelivery>()
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getUserId, userId)
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getSoftwareId, softwareId)
                        .eq(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getStatus, "SENT")
                        .select(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId));
        if (sent.isEmpty()) {
            return 0;
        }
        List<Long> ids = sent.stream()
                .map(com.cloudmart.software.entity.SoftwareSubscriptionDelivery::getId).toList();
        Long read = readMapper.selectCount(new LambdaQueryWrapper<SoftwareUpdateRead>()
                .eq(SoftwareUpdateRead::getUserId, userId)
                .in(SoftwareUpdateRead::getDeliveryId, ids));
        return ids.size() - read;
    }
}
