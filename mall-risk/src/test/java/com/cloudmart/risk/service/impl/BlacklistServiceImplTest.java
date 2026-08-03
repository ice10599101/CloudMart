package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.risk.entity.BlacklistEntry;
import com.cloudmart.risk.repository.BlacklistEntryRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlacklistServiceImplTest {

    private BlacklistEntryRepository blacklistEntryRepository;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private BlacklistServiceImpl blacklistService;

    private static final String TARGET_TYPE = "USER";
    private static final String TARGET_VALUE = "100";
    private static final String CACHE_KEY = "risk:blacklist:USER:100";

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(BlacklistEntry.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.risk.repository");
            TableInfoHelper.initTableInfo(assistant, BlacklistEntry.class);
        }
    }

    @BeforeEach
    void setUp() {
        blacklistEntryRepository = mock(BlacklistEntryRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        blacklistService = new BlacklistServiceImpl(blacklistEntryRepository, stringRedisTemplate);
    }

    @Nested
    @DisplayName("addToBlacklist")
    class AddToBlacklistTests {

        @Test
        @DisplayName("should insert entry and cache it in Redis")
        void addToBlacklist_insertsEntryAndCaches() {
            LocalDateTime expiredAt = LocalDateTime.now().plusDays(7);

            BlacklistEntry result = blacklistService.addToBlacklist(TARGET_TYPE, TARGET_VALUE, "恶意刷单", expiredAt);

            assertThat(result.getTargetType()).isEqualTo(TARGET_TYPE);
            assertThat(result.getTargetValue()).isEqualTo(TARGET_VALUE);
            assertThat(result.getReason()).isEqualTo("恶意刷单");
            assertThat(result.getExpiredAt()).isEqualTo(expiredAt);
            verify(blacklistEntryRepository).insert(any(BlacklistEntry.class));
            verify(valueOperations).set(eq(CACHE_KEY), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("should insert entry with null expiredAt for permanent blacklist")
        void addToBlacklist_permanentBlacklist_nullExpiredAt() {
            BlacklistEntry result = blacklistService.addToBlacklist("IP", "192.168.1.1", "异常访问", null);

            assertThat(result.getExpiredAt()).isNull();
            assertThat(result.getTargetType()).isEqualTo("IP");
            verify(blacklistEntryRepository).insert(any(BlacklistEntry.class));
        }
    }

    @Nested
    @DisplayName("removeFromBlacklist")
    class RemoveFromBlacklistTests {

        @Test
        @DisplayName("should delete entry from DB and remove cache")
        void removeFromBlacklist_deletesEntryAndCache() {
            blacklistService.removeFromBlacklist(TARGET_TYPE, TARGET_VALUE);

            verify(blacklistEntryRepository).delete(any(LambdaQueryWrapper.class));
            verify(stringRedisTemplate).delete(CACHE_KEY);
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklistedTests {

        @Test
        @DisplayName("should return true when found in Redis cache")
        void isBlacklisted_cached_returnsTrue() {
            when(valueOperations.get(CACHE_KEY)).thenReturn("1");

            boolean result = blacklistService.isBlacklisted(TARGET_TYPE, TARGET_VALUE);

            assertThat(result).isTrue();
            verify(blacklistEntryRepository, never()).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should return true when found in DB and not expired")
        void isBlacklisted_inDbNotExpired_returnsTrueAndCaches() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);

            BlacklistEntry entry = new BlacklistEntry();
            entry.setTargetType(TARGET_TYPE);
            entry.setTargetValue(TARGET_VALUE);
            entry.setExpiredAt(LocalDateTime.now().plusDays(1));
            when(blacklistEntryRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entry);

            boolean result = blacklistService.isBlacklisted(TARGET_TYPE, TARGET_VALUE);

            assertThat(result).isTrue();
            verify(valueOperations).set(eq(CACHE_KEY), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("should return false when entry in DB is expired")
        void isBlacklisted_expiredEntry_returnsFalse() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);

            BlacklistEntry entry = new BlacklistEntry();
            entry.setTargetType(TARGET_TYPE);
            entry.setTargetValue(TARGET_VALUE);
            entry.setExpiredAt(LocalDateTime.now().minusDays(1));
            when(blacklistEntryRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entry);

            boolean result = blacklistService.isBlacklisted(TARGET_TYPE, TARGET_VALUE);

            assertThat(result).isFalse();
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should return true when entry in DB has no expiration")
        void isBlacklisted_noExpiration_returnsTrue() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);

            BlacklistEntry entry = new BlacklistEntry();
            entry.setTargetType(TARGET_TYPE);
            entry.setTargetValue(TARGET_VALUE);
            entry.setExpiredAt(null);
            when(blacklistEntryRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entry);

            boolean result = blacklistService.isBlacklisted(TARGET_TYPE, TARGET_VALUE);

            assertThat(result).isTrue();
            verify(valueOperations).set(eq(CACHE_KEY), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("should return false when not found in cache or DB")
        void isBlacklisted_notInDb_returnsFalse() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(blacklistEntryRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            boolean result = blacklistService.isBlacklisted(TARGET_TYPE, TARGET_VALUE);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("listBlacklist")
    class ListBlacklistTests {

        @Test
        @DisplayName("should filter by targetType when provided")
        void listBlacklist_withTargetType_filtersByType() {
            BlacklistEntry entry = new BlacklistEntry();
            entry.setId(1L);
            entry.setTargetType("IP");
            entry.setTargetValue("192.168.1.1");

            Page<BlacklistEntry> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(entry));
            when(blacklistEntryRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = blacklistService.listBlacklist("IP", 1, 20);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().getFirst().getTargetType()).isEqualTo("IP");
        }

        @Test
        @DisplayName("should return all entries when targetType is null")
        void listBlacklist_withoutTargetType_returnsAll() {
            BlacklistEntry entry1 = new BlacklistEntry();
            entry1.setTargetType("USER");
            BlacklistEntry entry2 = new BlacklistEntry();
            entry2.setTargetType("IP");

            Page<BlacklistEntry> page = new Page<>(1, 20, 2L);
            page.setRecords(List.of(entry1, entry2));
            when(blacklistEntryRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = blacklistService.listBlacklist(null, 1, 20);

            assertThat(result.getRecords()).hasSize(2);
        }

        @Test
        @DisplayName("should return empty page when no entries exist")
        void listBlacklist_empty_returnsEmptyPage() {
            Page<BlacklistEntry> page = new Page<>(1, 20, 0L);
            page.setRecords(Collections.emptyList());
            when(blacklistEntryRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = blacklistService.listBlacklist(null, 1, 20);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
        }
    }
}
