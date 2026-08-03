package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.risk.entity.BlacklistEntry;
import com.cloudmart.risk.repository.BlacklistEntryRepository;
import com.cloudmart.risk.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BlacklistServiceImpl implements BlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "risk:blacklist:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final BlacklistEntryRepository blacklistEntryRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public BlacklistEntry addToBlacklist(String targetType, String targetValue, String reason, LocalDateTime expiredAt) {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setTargetType(targetType);
        entry.setTargetValue(targetValue);
        entry.setReason(reason);
        entry.setExpiredAt(expiredAt);
        blacklistEntryRepository.insert(entry);

        String cacheKey = buildCacheKey(targetType, targetValue);
        stringRedisTemplate.opsForValue().set(cacheKey, "1", CACHE_TTL);

        return entry;
    }

    @Override
    public void removeFromBlacklist(String targetType, String targetValue) {
        blacklistEntryRepository.delete(
                new LambdaQueryWrapper<BlacklistEntry>()
                        .eq(BlacklistEntry::getTargetType, targetType)
                        .eq(BlacklistEntry::getTargetValue, targetValue)
        );

        String cacheKey = buildCacheKey(targetType, targetValue);
        stringRedisTemplate.delete(cacheKey);
    }

    @Override
    public boolean isBlacklisted(String targetType, String targetValue) {
        String cacheKey = buildCacheKey(targetType, targetValue);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return true;
        }

        BlacklistEntry entry = blacklistEntryRepository.selectOne(
                new LambdaQueryWrapper<BlacklistEntry>()
                        .eq(BlacklistEntry::getTargetType, targetType)
                        .eq(BlacklistEntry::getTargetValue, targetValue)
        );

        if (entry == null) {
            return false;
        }

        if (entry.getExpiredAt() != null && entry.getExpiredAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        stringRedisTemplate.opsForValue().set(cacheKey, "1", CACHE_TTL);
        return true;
    }

    @Override
    public IPage<BlacklistEntry> listBlacklist(String targetType, int page, int pageSize) {
        Page<BlacklistEntry> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BlacklistEntry> wrapper = new LambdaQueryWrapper<>();
        if (targetType != null && !targetType.isEmpty()) {
            wrapper.eq(BlacklistEntry::getTargetType, targetType);
        }
        wrapper.orderByDesc(BlacklistEntry::getCreatedAt);
        return blacklistEntryRepository.selectPage(pageParam, wrapper);
    }

    private String buildCacheKey(String targetType, String targetValue) {
        return BLACKLIST_KEY_PREFIX + targetType + ":" + targetValue;
    }
}
