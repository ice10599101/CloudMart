package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminHomeSectionSaveRequest;
import com.cloudmart.software.entity.SoftwareHomeSection;
import com.cloudmart.software.repository.SoftwareHomeSectionItemMapper;
import com.cloudmart.software.repository.SoftwareHomeSectionMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareTopicMapper;
import com.cloudmart.software.service.SoftwareQueryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * UX01 单测：kind/data_source 白名单、slotKey 唯一、时间窗校验、
 * reorder 完整集合校验、投放期/平台范围过滤。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareHomeSectionServiceTest {

    @Mock
    private SoftwareHomeSectionMapper sectionMapper;
    @Mock
    private SoftwareHomeSectionItemMapper itemMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareTopicMapper topicMapper;
    @Mock
    private SoftwareQueryService softwareQueryService;

    private SoftwareHomeSectionServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareHomeSection.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareHomeSectionServiceImpl(sectionMapper, itemMapper,
                softwareMapper, topicMapper,
                org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareGuideMapper.class),
                softwareQueryService,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(sectionMapper.selectOne(any())).thenReturn(null);
    }

    private AdminHomeSectionSaveRequest validRequest() {
        AdminHomeSectionSaveRequest request = new AdminHomeSectionSaveRequest();
        request.setSlotKey("featured-rail");
        request.setTitle("今日推荐");
        request.setKind("SOFTWARE_GRID");
        request.setDataSource("FEATURED");
        request.setEnabled(true);
        request.setLimitCount(8);
        request.setMinItems(0);
        request.setFallbackPolicy("HIDE");
        return request;
    }

    @Test
    void save_validKindSource_ok() {
        service.saveSection(null, validRequest(), "k1");
        verify_insert();
    }

    private void verify_insert() {
        org.mockito.Mockito.verify(sectionMapper).insert(any(SoftwareHomeSection.class));
    }

    @Test
    void save_invalidKind_rejected() {
        AdminHomeSectionSaveRequest request = validRequest();
        request.setKind("CUSTOM_GRID");
        assertThatThrownBy(() -> service.saveSection(null, request, "k2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void save_invalidDataSource_rejected() {
        AdminHomeSectionSaveRequest request = validRequest();
        request.setDataSource("SQL_SCRIPT");
        assertThatThrownBy(() -> service.saveSection(null, request, "k3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dataSource");
    }

    @Test
    void save_duplicateSlotKey_rejected() {
        SoftwareHomeSection existing = new SoftwareHomeSection();
        existing.setId(99L);
        existing.setSlotKey("featured-rail");
        lenient().when(sectionMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.saveSection(null, validRequest(), "k4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slotKey");
    }

    @Test
    void save_startAfterEnd_rejected() {
        AdminHomeSectionSaveRequest request = validRequest();
        request.setStartAt(NOW.plusDays(1));
        request.setEndAt(NOW);
        assertThatThrownBy(() -> service.saveSection(null, request, "k5"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始");
    }

    @Test
    void reorder_missingSection_rejected() {
        SoftwareHomeSection a = new SoftwareHomeSection();
        a.setId(1L);
        a.setDeletedAt(null);
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reorder(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完整");
    }

    @Test
    void reorder_duplicate_rejected() {
        SoftwareHomeSection a = new SoftwareHomeSection();
        a.setId(1L);
        SoftwareHomeSection b = new SoftwareHomeSection();
        b.setId(2L);
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(List.of(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void publicHome_outOfWindow_hidden() {
        SoftwareHomeSection section = new SoftwareHomeSection();
        section.setId(1L);
        section.setSlotKey("s1");
        section.setTitle("t");
        section.setKind("SOFTWARE_GRID");
        section.setDataSource("FEATURED");
        section.setEnabled(true);
        section.setStartAt(NOW.plusDays(1)); // 未开始
        section.setLimitCount(8);
        section.setMinItems(0);
        section.setFallbackPolicy("HIDE");
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(section));

        assertThat(service.publicHome("WEB")).isEmpty();
    }

    @Test
    void publicHome_platformMismatch_hidden() {
        SoftwareHomeSection section = new SoftwareHomeSection();
        section.setId(1L);
        section.setSlotKey("s1");
        section.setTitle("t");
        section.setKind("SCENARIOS");
        section.setDataSource("TOPIC");
        section.setEnabled(true);
        section.setPlatformScope("APP,TARO"); // WEB 请求不匹配
        section.setLimitCount(8);
        section.setMinItems(0);
        section.setFallbackPolicy("HIDE");
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(section));

        assertThat(service.publicHome("WEB")).isEmpty();
    }

    @Test
    void publicHome_singleBlockFailure_isolated() {
        SoftwareHomeSection bad = new SoftwareHomeSection();
        bad.setId(1L);
        bad.setSlotKey("bad");
        bad.setTitle("坏块");
        bad.setKind("SOFTWARE_GRID");
        bad.setDataSource("FEATURED");
        bad.setEnabled(true);
        bad.setLimitCount(8);
        bad.setMinItems(0);
        bad.setFallbackPolicy("HIDE");
        SoftwareHomeSection good = new SoftwareHomeSection();
        good.setId(2L);
        good.setSlotKey("good");
        good.setTitle("好块");
        good.setKind("SOFTWARE_GRID");
        good.setDataSource("LATEST");
        good.setEnabled(true);
        good.setLimitCount(8);
        good.setMinItems(0);
        good.setFallbackPolicy("HIDE");
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(bad, good));
        lenient().when(softwareQueryService.pageCards(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenAnswer(inv -> {
                    Page<SoftwareCardVO> p = new Page<>(1, 8, 1);
                    p.setRecords(List.of());
                    return p;
                });
        List<java.util.Map<String, Object>> blocks = service.publicHome("WEB");
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).get("state")).isEqualTo("ERROR");
        assertThat(blocks.get(1).get("state")).isEqualTo("OK");
    }
}
