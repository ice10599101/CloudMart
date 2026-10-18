package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SW03：稳定键归属与处置连续性。
 *
 * <p>覆盖：跨软件伪造 releaseKey/sourceKey 拒绝、同快照重复键拒绝、
 * 既有键原样保留、新来源由服务端生成键、删除后重加同一链接继承 DISABLED。</p>
 */
class SoftwareSnapshotAssemblerStableKeyTest {

    private static final Long SOFTWARE_ID = 10L;
    private static final Long REVISION_ID = 100L;
    private static final String SOURCE_KEY = "11111111-1111-4111-8111-111111111111";
    private static final String FINGERPRINT = "f".repeat(64);

    private final SoftwareCategoryMapper categoryMapper = Mockito.mock(SoftwareCategoryMapper.class);
    private final SoftwareTagMapper tagMapper = Mockito.mock(SoftwareTagMapper.class);
    private final SoftwareAccessContactMapper accessContactMapper = Mockito.mock(SoftwareAccessContactMapper.class);
    private final SoftwareReleaseMapper releaseMapper = Mockito.mock(SoftwareReleaseMapper.class);
    private final SoftwareSourceMapper sourceMapper = Mockito.mock(SoftwareSourceMapper.class);
    private final SoftwareRevisionMapper revisionMapper = Mockito.mock(SoftwareRevisionMapper.class);
    private final SoftwareSourceStateMapper sourceStateMapper = Mockito.mock(SoftwareSourceStateMapper.class);
    private final SoftwareRevisionTagMapper revisionTagMapper = Mockito.mock(SoftwareRevisionTagMapper.class);
    private final SoftwareRevisionMediaMapper revisionMediaMapper = Mockito.mock(SoftwareRevisionMediaMapper.class);
    private final SoftwareHtmlSanitizer sanitizer = Mockito.mock(SoftwareHtmlSanitizer.class);
    private final SoftwareLinkValidator linkValidator = Mockito.mock(SoftwareLinkValidator.class);
    private final CommunitySupportFeignClient communitySupportFeignClient =
            Mockito.mock(CommunitySupportFeignClient.class);
    private final SoftwareProperties softwareProperties = Mockito.mock(SoftwareProperties.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SoftwareSnapshotAssembler assembler;
    private SoftwareRevision revision;

    @BeforeEach
    void setUp() {
        assembler = new SoftwareSnapshotAssembler(categoryMapper, tagMapper, accessContactMapper,
                releaseMapper, sourceMapper, revisionMapper, sourceStateMapper, revisionTagMapper,
                revisionMediaMapper, sanitizer, linkValidator, communitySupportFeignClient,
                softwareProperties, objectMapper);
        revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        given(revisionMapper.selectById(REVISION_ID)).willReturn(revision);
        given(revisionMapper.selectList(any())).willReturn(List.of(revision));
        given(releaseMapper.selectList(any())).willReturn(List.of());
        given(sourceMapper.selectList(any())).willReturn(List.of());
        given(sourceStateMapper.selectList(any())).willReturn(List.of());
        given(softwareProperties.getRevisionsMaxReleases()).willReturn(50);
        given(softwareProperties.getReleaseMaxSources()).willReturn(8);
        given(linkValidator.validate(any(), any(), any(), any(), any()))
                .willReturn(new SoftwareLinkValidator.ValidatedLink(
                        "https://pan.example.com/s/1", "https://pan.example.com/s/1",
                        "hash-1", FINGERPRINT, null, null, SoftwareProvider.BAIDU));
    }

    private SoftwareRelease lineageRelease() {
        SoftwareRelease release = new SoftwareRelease();
        release.setId(1L);
        release.setRevisionId(REVISION_ID);
        release.setReleaseKey("22222222-2222-4222-8222-222222222222");
        release.setChannel(com.cloudmart.software.enums.SoftwareChannel.STABLE);
        release.setRecommended(false);
        return release;
    }

    private SoftwareSource lineageSource() {
        SoftwareSource source = new SoftwareSource();
        source.setId(2L);
        source.setReleaseId(1L);
        source.setSourceKey(SOURCE_KEY);
        source.setContentFingerprint(FINGERPRINT);
        source.setUrlHash("hash-1");
        return source;
    }

    private JsonNode releasePatch(String releaseKey, String sourceKey) {
        String releaseKeyJson = releaseKey == null ? "null" : "\"" + releaseKey + "\"";
        String sourceKeyJson = sourceKey == null ? "null" : "\"" + sourceKey + "\"";
        return objectMapper.readTree("""
                {"releases":[{"releaseKey":%s,"versionName":"1.0.0","sources":[
                  {"sourceKey":%s,"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64",
                   "label":"网盘","url":"https://pan.example.com/s/1"}]}]}
                """.formatted(releaseKeyJson, sourceKeyJson));
    }

    @Test
    @DisplayName("跨软件伪造 releaseKey 被拒绝，且不写任何版本行")
    void forgedReleaseKeyRejected() {
        String forged = UUID.randomUUID().toString();
        assertThatThrownBy(() -> assembler.applyPatch(revision, releasePatch(forged, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于本软件");
        verify(releaseMapper, Mockito.never()).insert(any(SoftwareRelease.class));
    }

    @Test
    @DisplayName("跨软件伪造 sourceKey 被拒绝")
    void forgedSourceKeyRejected() {
        String forged = UUID.randomUUID().toString();
        assertThatThrownBy(() -> assembler.applyPatch(revision, releasePatch(null, forged)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sourceKey 不属于本软件");
    }

    @Test
    @DisplayName("同一快照内重复 sourceKey 被拒绝")
    void duplicateSourceKeyRejected() {
        given(releaseMapper.selectList(any())).willReturn(List.of(lineageRelease()));
        given(sourceMapper.selectList(any())).willReturn(List.of(lineageSource()));
        JsonNode patch = objectMapper.readTree("""
                {"releases":[{"versionName":"1.0.0","sources":[
                  {"sourceKey":"%s","sourceType":"NETDISK","platform":"WINDOWS","arch":"X64",
                   "label":"网盘","url":"https://pan.example.com/s/1"},
                  {"sourceKey":"%s","sourceType":"NETDISK","platform":"MACOS","arch":"ARM64",
                   "label":"网盘","url":"https://pan.example.com/s/2"}]}]}
                """.formatted(SOURCE_KEY, SOURCE_KEY));
        assertThatThrownBy(() -> assembler.applyPatch(revision, patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sourceKey 不能重复");
    }

    @Test
    @DisplayName("既有稳定键原样保留，新来源由服务端生成 UUID")
    void existingKeyPreservedAndNewKeyGenerated() {
        given(releaseMapper.selectList(any())).willReturn(List.of(lineageRelease()));
        given(sourceMapper.selectList(any())).willReturn(List.of(lineageSource()));
        JsonNode patch = objectMapper.readTree("""
                {"releases":[{"versionName":"2.0.0","sources":[
                  {"sourceKey":"%s","sourceType":"NETDISK","platform":"WINDOWS","arch":"X64",
                   "label":"网盘","url":"https://pan.example.com/s/1"},
                  {"sourceType":"NETDISK","platform":"MACOS","arch":"ARM64",
                   "label":"网盘","url":"https://pan.example.com/s/2"}]}]}
                """.formatted(SOURCE_KEY));

        assembler.applyPatch(revision, patch);

        ArgumentCaptor<SoftwareSource> captor = ArgumentCaptor.forClass(SoftwareSource.class);
        verify(sourceMapper, Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getSourceKey()).isEqualTo(SOURCE_KEY);
        String generated = captor.getAllValues().get(1).getSourceKey();
        assertThat(generated).isNotEqualTo(SOURCE_KEY);
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    @DisplayName("删除后重加同一链接：新键继承 DISABLED，普通编辑不能洗白")
    void deletedThenReaddedSourceInheritsDisabled() {
        SoftwareRelease lineage = lineageRelease();
        given(releaseMapper.selectList(any())).willReturn(List.of(lineage));
        given(sourceMapper.selectList(any())).willReturn(List.of(lineageSource()));
        SoftwareSourceState disabled = new SoftwareSourceState();
        disabled.setSoftwareId(SOFTWARE_ID);
        disabled.setSourceKey(SOURCE_KEY);
        disabled.setAvailability(SourceAvailability.DISABLED);
        disabled.setCheckedFingerprint(FINGERPRINT);
        disabled.setReason("人工判定失效");
        given(sourceStateMapper.selectList(any())).willReturn(List.of(disabled));

        assembler.applyPatch(revision, releasePatch(null, null));

        ArgumentCaptor<SoftwareSourceState> captor =
                ArgumentCaptor.forClass(SoftwareSourceState.class);
        verify(sourceStateMapper).insert(captor.capture());
        SoftwareSourceState inherited = captor.getValue();
        assertThat(inherited.getAvailability()).isEqualTo(SourceAvailability.DISABLED);
        assertThat(inherited.getCheckedFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(inherited.getReason()).isEqualTo("人工判定失效");
        assertThat(inherited.getSourceKey()).isNotEqualTo(SOURCE_KEY);
    }

    @Test
    @DisplayName("链接变化后按新链接重新计算指纹，不继承旧 DISABLED")
    void changedUrlDoesNotInheritDisabled() {
        given(releaseMapper.selectList(any())).willReturn(List.of(lineageRelease()));
        given(sourceMapper.selectList(any())).willReturn(List.of(lineageSource()));
        SoftwareSourceState disabled = new SoftwareSourceState();
        disabled.setSoftwareId(SOFTWARE_ID);
        disabled.setSourceKey(SOURCE_KEY);
        disabled.setAvailability(SourceAvailability.DISABLED);
        given(sourceStateMapper.selectList(any())).willReturn(List.of(disabled));
        given(linkValidator.validate(any(), any(), any(), any(), any()))
                .willReturn(new SoftwareLinkValidator.ValidatedLink(
                        "https://pan.example.com/s/new", "https://pan.example.com/s/new",
                        "hash-new", "a".repeat(64), null, null, SoftwareProvider.BAIDU));

        assembler.applyPatch(revision, releasePatch(null, null));

        verify(sourceStateMapper, Mockito.never()).insert(any(SoftwareSourceState.class));
    }
}
