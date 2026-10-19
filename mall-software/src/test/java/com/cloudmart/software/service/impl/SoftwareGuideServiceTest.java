package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminGuideSaveRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareGuide;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareGuideMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

/**
 * UX04 单测：教程元数据校验（类型/文章唯一/关联软件必填且已发布/
 * 文章公开预检/公开读取实时隐藏）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareGuideServiceTest {

    @Mock
    private SoftwareGuideMapper guideMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private CommunitySupportFeignClient communitySupportFeignClient;

    private SoftwareGuideServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareGuide.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareGuideServiceImpl(guideMapper, softwareMapper, revisionMapper,
                communitySupportFeignClient,
                Clock.fixed(LocalDateTime.of(2026, 10, 20, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(guideMapper.selectOne(any())).thenReturn(null);
    }

    private AdminGuideSaveRequest validRequest() {
        AdminGuideSaveRequest request = new AdminGuideSaveRequest();
        request.setGuideType("INSTALL_GUIDE");
        request.setTitle("新电脑安装入门");
        request.setArticleId(101L);
        request.setRelatedSoftwareIds(List.of(7L));
        return request;
    }

    private void mockArticlePublic(boolean isPublic) {
        Map<String, Object> row = new HashMap<>();
        row.put("postId", "101");
        row.put("public", isPublic);
        if (isPublic) {
            row.put("title", "社区文章标题");
        }
        com.cloudmart.common.api.ApiResponse<List<Map<String, Object>>> resp =
                com.cloudmart.common.api.ApiResponse.ok(List.of(row));
        lenient().when(communitySupportFeignClient.postPublicStatus(anyList())).thenReturn(resp);
    }

    private void mockSoftwarePublished() {
        Software software = new Software();
        software.setId(7L);
        software.setPublishedRevisionId(70L);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        lenient().when(softwareMapper.selectById(7L)).thenReturn(software);
    }

    @Test
    void save_valid_ok() {
        mockArticlePublic(true);
        mockSoftwarePublished();
        service.saveGuide(null, validRequest(), "k1");
        org.mockito.Mockito.verify(guideMapper).insert(any(SoftwareGuide.class));
    }

    @Test
    void save_invalidType_rejected() {
        AdminGuideSaveRequest request = validRequest();
        request.setGuideType("NEWS");
        assertThatThrownBy(() -> service.saveGuide(null, request, "k2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("guideType");
    }

    @Test
    void save_articleNotPublic_rejected() {
        mockArticlePublic(false);
        mockSoftwarePublished();
        assertThatThrownBy(() -> service.saveGuide(null, validRequest(), "k3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非公开");
    }

    @Test
    void save_noRelation_rejected() {
        mockArticlePublic(true);
        AdminGuideSaveRequest request = validRequest();
        request.setRelatedSoftwareIds(List.of());
        assertThatThrownBy(() -> service.saveGuide(null, request, "k4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少关联");
    }

    @Test
    void save_unpublishedTarget_rejected() {
        mockArticlePublic(true);
        Software draft = new Software();
        draft.setId(7L);
        draft.setPublishedRevisionId(null);
        lenient().when(softwareMapper.selectById(7L)).thenReturn(draft);
        assertThatThrownBy(() -> service.saveGuide(null, validRequest(), "k5"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未发布");
    }

    @Test
    void save_duplicateArticle_rejected() {
        mockArticlePublic(true);
        mockSoftwarePublished();
        SoftwareGuide existing = new SoftwareGuide();
        existing.setId(9L);
        existing.setArticleId(101L);
        lenient().when(guideMapper.selectOne(any())).thenReturn(existing);
        assertThatThrownBy(() -> service.saveGuide(null, validRequest(), "k6"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关联");
    }

    @Test
    void publicDetail_articleWithdrawn_hidden() {
        SoftwareGuide guide = new SoftwareGuide();
        guide.setId(1L);
        guide.setGuideType("INSTALL_GUIDE");
        guide.setTitle("t");
        guide.setArticleId(101L);
        guide.setEnabled(true);
        guide.setDeletedAt(null);
        lenient().when(guideMapper.selectById(1L)).thenReturn(guide);
        mockArticlePublic(false); // 文章撤回

        assertThatThrownBy(() -> service.publicGuideDetail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教程不存在"); // 404 语义
    }

    @Test
    void publicList_articleWithdrawn_filtered() {
        SoftwareGuide guide = new SoftwareGuide();
        guide.setId(1L);
        guide.setGuideType("INSTALL_GUIDE");
        guide.setTitle("t");
        guide.setArticleId(101L);
        guide.setEnabled(true);
        guide.setDeletedAt(null);
        Page<SoftwareGuide> p = new Page<>(1, 12, 1);
        p.setRecords(List.of(guide));
        lenient().when(guideMapper.selectPage(any(), any())).thenReturn(p);
        mockArticlePublic(false);

        Map<String, Object> result = service.publicGuides(null, 1, 12);
        assertThat((List<?>) result.get("records")).isEmpty(); // 宁缺勿假
    }
}
