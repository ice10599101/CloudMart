package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.repository.SoftwareRankingMapper;
import com.cloudmart.software.service.SoftwareQueryService;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * P1 榜单单测：独立开关（11.2）、聚合 ID 顺序保留、实时可见性依赖 cardsByIds 过滤。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRankingServiceImplTest {

    @Mock
    private SoftwareRankingMapper rankingMapper;
    @Mock
    private SoftwareQueryService queryService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    private SoftwareProperties properties;
    private SoftwareRankingServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new SoftwareProperties();
        properties.setRankingsEnabled(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any(String.class))).thenReturn(null);
        service = new SoftwareRankingServiceImpl(rankingMapper, properties, queryService,
                redisTemplate, objectMapper,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("11.2：榜单独立开关关闭时服务端拒绝")
    void disabled_throws() {
        properties.setRankingsEnabled(false);

        assertThatThrownBy(() -> service.rankings("access", 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_RANKINGS_DISABLED");
    }

    @Test
    @DisplayName("获取榜：聚合 ID 顺序保留；被过滤（不可见）的软件从结果剔除")
    void access_preservesOrderAndFiltersInvisible() {
        when(rankingMapper.topIdsByAccess7d(any(LocalDate.class), anyInt()))
                .thenReturn(List.of(3L, 2L, 1L));
        // 软件 2 不可见（下架），cardsByIds 不返回
        when(queryService.cardsByIds(List.of(3L, 2L, 1L)))
                .thenReturn(List.of(card(3L), card(1L)));

        Page<SoftwareCardVO> page = service.rankings("access", 1, 10);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).extracting(SoftwareCardVO::id).containsExactly(3L, 1L);
    }

    @Test
    @DisplayName("评分榜：使用总体均值参数且只看至少 5 人评分（由 SQL 保证，此处校验透传）")
    void rating_passesOverallMean() {
        when(rankingMapper.overallRatingMean()).thenReturn(4.2);
        when(rankingMapper.topIdsByRating(4.2, 100)).thenReturn(List.of(7L));
        when(queryService.cardsByIds(List.of(7L))).thenReturn(List.of(card(7L)));

        Page<SoftwareCardVO> page = service.rankings("rating", 1, 10);

        assertThat(page.getRecords()).extracting(SoftwareCardVO::id).containsExactly(7L);
    }

    private SoftwareCardVO card(Long id) {
        return new SoftwareCardVO(id, "name-" + id, "summary", null, 1L, "效率办公", "FREE",
                List.of("WINDOWS"), null, 0, 0, 0, null);
    }
}
