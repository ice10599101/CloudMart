package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SW02：PATCH 局部保存语义——缺省=不修改、显式 null=清空可空标量、
 * 集合必须显式数组、未知字段拒绝。重点防止“只改名称却清空其它字段”。
 */
class SoftwareSnapshotAssemblerPatchTest {

    private final SoftwareCategoryMapper categoryMapper = Mockito.mock(SoftwareCategoryMapper.class);
    private final SoftwareTagMapper tagMapper = Mockito.mock(SoftwareTagMapper.class);
    private final SoftwareAccessContactMapper accessContactMapper = Mockito.mock(SoftwareAccessContactMapper.class);
    private final SoftwareReleaseMapper releaseMapper = Mockito.mock(SoftwareReleaseMapper.class);
    private final SoftwareSourceMapper sourceMapper = Mockito.mock(SoftwareSourceMapper.class);
    private final SoftwareRevisionTagMapper revisionTagMapper = Mockito.mock(SoftwareRevisionTagMapper.class);
    private final SoftwareRevisionMediaMapper revisionMediaMapper = Mockito.mock(SoftwareRevisionMediaMapper.class);
    private final SoftwareHtmlSanitizer sanitizer = Mockito.mock(SoftwareHtmlSanitizer.class);
    private final SoftwareLinkValidator linkValidator = Mockito.mock(SoftwareLinkValidator.class);
    private final CommunitySupportFeignClient communitySupportFeignClient =
            Mockito.mock(CommunitySupportFeignClient.class);
    private final SoftwareProperties softwareProperties = Mockito.mock(SoftwareProperties.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SoftwareSnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SoftwareSnapshotAssembler(categoryMapper, tagMapper, accessContactMapper,
                releaseMapper, sourceMapper, revisionTagMapper, revisionMediaMapper, sanitizer,
                linkValidator, communitySupportFeignClient, softwareProperties, objectMapper);
        given(sanitizer.extractText(any())).willReturn("text");
        given(softwareProperties.getScreenshotMaxCount()).willReturn(8);
    }

    private SoftwareRevision baseRevision() {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(100L);
        revision.setSoftwareId(10L);
        revision.setName("原名称");
        revision.setSummary("原简介内容");
        revision.setDeveloperName("原开发者");
        revision.setOfficialUrl("https://example.com");
        revision.setDescriptionHtml("<p>原文</p>");
        revision.setLanguages("[\"zh-CN\"]");
        revision.setAccessMode(SoftwareAccessMode.REGISTERED);
        revision.setAttributionNote("原备注");
        revision.setSystemRequirements("Windows 10");
        return revision;
    }

    @Test
    @DisplayName("只提交 name：其它标量与子集合完全不变，不触发任何子表删除")
    void nameOnlyKeepsEverythingElse() {
        SoftwareRevision revision = baseRevision();
        JsonNode patch = objectMapper.readTree("{\"name\":\"新名称\"}");

        assembler.applyPatch(revision, patch);

        assertThat(revision.getName()).isEqualTo("新名称");
        assertThat(revision.getSummary()).isEqualTo("原简介内容");
        assertThat(revision.getDeveloperName()).isEqualTo("原开发者");
        assertThat(revision.getOfficialUrl()).isEqualTo("https://example.com");
        assertThat(revision.getLanguages()).isEqualTo("[\"zh-CN\"]");
        assertThat(revision.getAttributionNote()).isEqualTo("原备注");
        assertThat(revision.getSystemRequirements()).isEqualTo("Windows 10");
        verify(revisionTagMapper, never()).delete(any());
        verify(revisionMediaMapper, never()).delete(any());
        verify(releaseMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("显式 null 清空可空标量；未出现的字段保持原值")
    void explicitNullClearsNullableScalar() {
        SoftwareRevision revision = baseRevision();
        JsonNode patch = objectMapper.readTree(
                "{\"developerName\":null,\"officialUrl\":null,\"summary\":null}");

        assembler.applyPatch(revision, patch);

        assertThat(revision.getDeveloperName()).isNull();
        assertThat(revision.getOfficialUrl()).isNull();
        assertThat(revision.getSummary()).isEmpty();
        assertThat(revision.getName()).isEqualTo("原名称");
    }

    @Test
    @DisplayName("未知字段直接拒绝，避免拼写错误静默丢数据")
    void unknownFieldRejected() {
        SoftwareRevision revision = baseRevision();
        JsonNode patch = objectMapper.readTree("{\"nmae\":\"拼错\"}");

        assertThatThrownBy(() -> assembler.applyPatch(revision, patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的字段")
                .hasMessageContaining("nmae");
        assertThat(revision.getName()).isEqualTo("原名称");
    }

    @Test
    @DisplayName("集合字段显式 null 非法；[] 才表示清空")
    void collectionNullRejectedAndEmptyArrayClears() {
        SoftwareRevision revision = baseRevision();
        JsonNode nullReleases = objectMapper.readTree("{\"releases\":null}");
        assertThatThrownBy(() -> assembler.applyPatch(revision, nullReleases))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("releases");

        JsonNode emptyLanguages = objectMapper.readTree("{\"languages\":[]}");
        assembler.applyPatch(revision, emptyLanguages);
        assertThat(revision.getLanguages()).isNull();
    }

    @Test
    @DisplayName("截图出现即整体替换：删除旧行并写入新行")
    void screenshotsPresentReplacesCollection() {
        SoftwareRevision revision = baseRevision();
        JsonNode patch = objectMapper.readTree(
                "{\"screenshots\":[{\"assetId\":\"9001\",\"altText\":\"首屏\",\"displayOrder\":0}]}");

        assembler.applyPatch(revision, patch);

        verify(revisionMediaMapper).delete(any());
        verify(revisionMediaMapper).insert(any(SoftwareRevisionMedia.class));
    }

    @Test
    @DisplayName("查看条件联动：切到 REGISTERED 时清空等级与联系入口")
    void accessModeSwitchClearsIncompatibleFields() {
        SoftwareRevision revision = baseRevision();
        revision.setAccessMode(SoftwareAccessMode.ADMIN_PAID);
        revision.setAccessContactId(77L);
        JsonNode patch = objectMapper.readTree("{\"accessMode\":\"REGISTERED\"}");

        assembler.applyPatch(revision, patch);

        assertThat(revision.getAccessMode()).isEqualTo(SoftwareAccessMode.REGISTERED);
        assertThat(revision.getMinCommunityLevel()).isNull();
        assertThat(revision.getAccessContactId()).isNull();
    }

    @Test
    @DisplayName("只改最低等级：保持 COMMUNITY_LEVEL 组合，不误清已设置的等级")
    void partialAccessModeKeepsOtherField() {
        SoftwareRevision revision = baseRevision();
        revision.setAccessMode(SoftwareAccessMode.COMMUNITY_LEVEL);
        revision.setMinCommunityLevel(2);
        JsonNode patch = objectMapper.readTree("{\"accessContactId\":null}");

        assembler.applyPatch(revision, patch);

        assertThat(revision.getAccessMode()).isEqualTo(SoftwareAccessMode.COMMUNITY_LEVEL);
        assertThat(revision.getMinCommunityLevel()).isEqualTo(2);
        assertThat(revision.getAccessContactId()).isNull();
    }

    @Test
    @DisplayName("语言数组超过 20 项被拒绝")
    void languagesLimitEnforced() {
        SoftwareRevision revision = baseRevision();
        List<String> tooMany = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "L" + i).toList();
        JsonNode patch = objectMapper.valueToTree(java.util.Map.of("languages", tooMany));

        assertThatThrownBy(() -> assembler.applyPatch(revision, patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("语言列表最多 20 项");
    }
}
