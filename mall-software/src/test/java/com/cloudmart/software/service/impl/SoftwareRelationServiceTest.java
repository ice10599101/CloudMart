package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminRelationSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRelation;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRelationMapper;
import com.cloudmart.software.service.SoftwareSnapshotAssembler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * UX03 单测：短亮点校验（≤3 条/60 字）与同类关系校验（自关联/去重/目标已发布）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRelationServiceTest {

    @Mock
    private SoftwareRelationMapper relationMapper;
    @Mock
    private SoftwareMapper softwareMapper;

    private SoftwareRelationServiceImpl service;

    private final SoftwareSnapshotAssembler assembler =
            new SoftwareSnapshotAssembler(
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareCategoryMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareTagMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareAccessContactMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareReleaseMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareSourceMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareRevisionMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareSourceStateMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareRevisionTagMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.repository.SoftwareRevisionMediaMapper.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.service.SoftwareHtmlSanitizer.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.service.SoftwareLinkValidator.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.feign.CommunitySupportFeignClient.class),
                    org.mockito.Mockito.mock(com.cloudmart.software.config.SoftwareProperties.class),
                    new tools.jackson.databind.ObjectMapper());

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                com.cloudmart.software.entity.SoftwareRelation.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareRelationServiceImpl(relationMapper, softwareMapper);
        // 目标软件存在且已发布
        Software target = new Software();
        target.setId(9L);
        target.setPublishedRevisionId(100L);
        target.setLifecycle(com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE);
        lenient().when(softwareMapper.selectById(9L)).thenReturn(target);
    }

    // ---------------- highlights ----------------

    @Test
    void highlights_threeValid_ok() {
        String json = assembler.serializeHighlights(List.of("一键安装", "绿色便携", "开源免费"));
        assertThat(json).contains("一键安装");
    }

    @Test
    void highlights_overLimit_rejected() {
        assertThatThrownBy(() -> assembler.serializeHighlights(
                List.of("a", "b", "c", "d")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_SNAPSHOT_INVALID");
    }

    @Test
    void highlights_tooLong_rejected() {
        assertThatThrownBy(() -> assembler.serializeHighlights(List.of("字".repeat(61))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void highlights_empty_returnsNull() {
        assertThat(assembler.serializeHighlights(List.of())).isNull();
    }

    // ---------------- relations ----------------

    @Test
    void relation_selfReference_rejected() {
        AdminRelationSaveRequest request = new AdminRelationSaveRequest();
        AdminRelationSaveRequest.Item item = new AdminRelationSaveRequest.Item();
        item.setRelatedId(2L);
        item.setRelationType("SIMILAR_USE");
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.saveRelations(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_RELATION_INVALID");
    }

    @Test
    void relation_duplicateType_rejected() {
        AdminRelationSaveRequest request = new AdminRelationSaveRequest();
        AdminRelationSaveRequest.Item a = new AdminRelationSaveRequest.Item();
        a.setRelatedId(9L);
        a.setRelationType("SIMILAR_USE");
        AdminRelationSaveRequest.Item b = new AdminRelationSaveRequest.Item();
        b.setRelatedId(9L);
        b.setRelationType("SIMILAR_USE");
        request.setItems(List.of(a, b));

        assertThatThrownBy(() -> service.saveRelations(2L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void relation_targetUnpublished_rejected() {
        Software draft = new Software();
        draft.setId(10L);
        draft.setPublishedRevisionId(null);
        draft.setLifecycle(com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE);
        lenient().when(softwareMapper.selectById(10L)).thenReturn(draft);

        AdminRelationSaveRequest request = new AdminRelationSaveRequest();
        AdminRelationSaveRequest.Item item = new AdminRelationSaveRequest.Item();
        item.setRelatedId(10L);
        item.setRelationType("SIMILAR_USE");
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.saveRelations(2L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未发布");
    }

    @Test
    void relation_valid_replaceAll() {
        AdminRelationSaveRequest request = new AdminRelationSaveRequest();
        AdminRelationSaveRequest.Item a = new AdminRelationSaveRequest.Item();
        a.setRelatedId(9L);
        a.setRelationType("SIMILAR_USE");
        a.setReason("同类用途");
        AdminRelationSaveRequest.Item b = new AdminRelationSaveRequest.Item();
        b.setRelatedId(9L);
        b.setRelationType("ALTERNATIVE_PLATFORM");
        request.setItems(List.of(a, b));

        int n = service.saveRelations(2L, request);
        assertThat(n).isEqualTo(2);
        verify(relationMapper).delete(any());
        ArgumentCaptor<SoftwareRelation> captor = ArgumentCaptor.forClass(SoftwareRelation.class);
        verify(relationMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SoftwareRelation::getRelationType)
                .containsExactly("SIMILAR_USE", "ALTERNATIVE_PLATFORM");
    }
}
