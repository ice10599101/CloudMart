package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishMapProperties;
import com.cloudmart.wish.entity.LiveWidgetConfig;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.enums.WidgetPosition;
import com.cloudmart.wish.repository.LiveWidgetConfigMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.GrayscaleService;
import com.cloudmart.wish.service.LiveWidgetService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.LiveWidgetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 直播心愿挂件服务实现（Sprint 3.4）。
 *
 * <p>数据聚合：主播最新 ACTIVE 公开心愿 + wish_progress（进度）+
 * wish_user_stat（打卡天数/星光余额）。Redis 缓存 TTL 10s + 抖动——
 * 验收"主播打卡/点亮后挂件 10s 内更新" + "1000 观众轮询无压力"。</p>
 *
 * <p>隐私：仅返回心愿进度/打卡天数/星光，不含手机号/邮箱等个人字段。
 * 全局降级：灰度 feature wish_live_widget 比例 0 → visible=false
 * （挂件隐藏，直播正常）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveWidgetServiceImpl implements LiveWidgetService {

    private static final String CACHE_KEY_PREFIX = "live:widget:";
    private static final int CACHE_TTL_SECONDS = 10;

    private final LiveWidgetConfigMapper configMapper;
    private final WishMapper wishMapper;
    private final WishProgressMapper progressMapper;
    private final WishUserStatMapper userStatMapper;
    private final GrayscaleService grayscaleService;
    private final WishMapProperties mapProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LiveWidgetVO getWidgetData(Long streamerId) {
        // Redis 缓存（TTL 10s + 抖动；Fail-Open 直查聚合）
        String cacheKey = CACHE_KEY_PREFIX + streamerId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, LiveWidgetVO.class);
            }
        } catch (DataAccessException ex) {
            log.warn("挂件缓存读取失败（Fail-Open 直查聚合）: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("挂件缓存反序列化失败，视为未命中: {}", ex.getMessage());
        }

        LiveWidgetVO vo = assemble(streamerId);
        try {
            long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, 3);
            redisTemplate.opsForValue().set(cacheKey,
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vo),
                    Duration.ofSeconds(ttl));
        } catch (Exception ex) {
            log.warn("挂件缓存写入失败（Fail-Open）: {}", ex.getMessage());
        }
        return vo;
    }

    private LiveWidgetVO assemble(Long streamerId) {
        boolean globalOn = grayscaleService.isEnabled(null, "wish_live_widget");
        LiveWidgetConfig config = configMapper.selectOne(new LambdaQueryWrapper<LiveWidgetConfig>()
                .eq(LiveWidgetConfig::getStreamerId, streamerId)
                .last("LIMIT 1"));
        boolean visible = globalOn && (config == null || !Boolean.FALSE.equals(config.getIsVisible()));
        String position = config != null && config.getPosition() != null
                ? config.getPosition().name() : WidgetPosition.BOTTOM_RIGHT.name();
        String styleConfig = config != null ? config.getStyleConfig() : null;

        // 主播最新进行中的公开心愿（无 → hasWish=false，前端"去许愿"引导）
        Wish wish = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, streamerId)
                        .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                        .eq(Wish::getStatus, WishStatus.ACTIVE)
                        .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                        .eq(Wish::getIsVisible, true)
                        .orderByDesc(Wish::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);

        if (wish == null) {
            return new LiveWidgetVO(streamerId, visible, false, null, null,
                    null, null, null, null, null, position, styleConfig);
        }

        WishProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<WishProgress>()
                .eq(WishProgress::getWishId, wish.getId())
                .last("LIMIT 1"));
        WishUserStat stat = userStatMapper.selectById(streamerId);

        int current = progress != null && progress.getCurrentValue() != null ? progress.getCurrentValue() : 0;
        int target = progress != null && progress.getTargetValue() != null ? progress.getTargetValue() : 0;
        int percentage = target > 0 ? Math.min(100, Math.round(current * 100.0f / target)) : 0;

        return new LiveWidgetVO(
                streamerId,
                visible,
                true,
                wish.getId(),
                wish.getTitle(),
                current,
                target,
                percentage,
                stat != null && stat.getTotalCheckinDays() != null ? stat.getTotalCheckinDays() : 0,
                stat != null && stat.getStarlightBalance() != null ? stat.getStarlightBalance() : 0,
                position,
                styleConfig);
    }

    // ---------------- 管理端 ----------------

    @Override
    public List<LiveWidgetConfig> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<LiveWidgetConfig>()
                .orderByDesc(LiveWidgetConfig::getUpdatedAt));
    }

    @Override
    @Transactional
    public LiveWidgetConfig saveConfig(LiveWidgetConfig config) {
        if (config.getStreamerId() == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "主播 ID 不能为空");
        }
        if (config.getPosition() != null) {
            try {
                WidgetPosition.valueOf(config.getPosition().name());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "挂件位置非法");
            }
        }
        if (config.getStyleConfig() != null && !config.getStyleConfig().isBlank()) {
            // style JSON 校验（解析失败即非法）
            try {
                WishJsonUtils.parseStringList(config.getStyleConfig());
            } catch (Exception ignore) {
                // 非数组 JSON 也允许（样式对象），仅做 JSON 可解析性校验
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(config.getStyleConfig());
                } catch (Exception ex) {
                    throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "样式配置须为合法 JSON");
                }
            }
        }
        LiveWidgetConfig existing = configMapper.selectOne(new LambdaQueryWrapper<LiveWidgetConfig>()
                .eq(LiveWidgetConfig::getStreamerId, config.getStreamerId())
                .last("LIMIT 1"));
        if (existing == null) {
            config.setIsVisible(config.getIsVisible() == null || config.getIsVisible());
            configMapper.insert(config);
        } else {
            LiveWidgetConfig update = new LiveWidgetConfig();
            update.setId(existing.getId());
            update.setPosition(config.getPosition());
            update.setStyleConfig(config.getStyleConfig());
            update.setIsVisible(config.getIsVisible());
            update.setUpdatedBy(config.getUpdatedBy());
            configMapper.updateById(update);
        }
        evictCache(config.getStreamerId());
        LiveWidgetConfig fresh = configMapper.selectOne(new LambdaQueryWrapper<LiveWidgetConfig>()
                .eq(LiveWidgetConfig::getStreamerId, config.getStreamerId())
                .last("LIMIT 1"));
        log.info("直播挂件配置已保存, streamerId={}, adminUserId={}", config.getStreamerId(), config.getUpdatedBy());
        return fresh;
    }

    @Override
    @Transactional
    public void toggleConfig(Long streamerId, boolean visible) {
        LiveWidgetConfig config = configMapper.selectOne(new LambdaQueryWrapper<LiveWidgetConfig>()
                .eq(LiveWidgetConfig::getStreamerId, streamerId)
                .last("LIMIT 1"));
        if (config == null) {
            LiveWidgetConfig insert = new LiveWidgetConfig();
            insert.setStreamerId(streamerId);
            insert.setIsVisible(visible);
            configMapper.insert(insert);
        } else {
            LiveWidgetConfig update = new LiveWidgetConfig();
            update.setId(config.getId());
            update.setIsVisible(visible);
            configMapper.updateById(update);
        }
        evictCache(streamerId);
    }

    private void evictCache(Long streamerId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + streamerId);
        } catch (DataAccessException ex) {
            log.warn("挂件缓存清理失败（TTL 10s 兜底）: {}", ex.getMessage());
        }
    }
}
