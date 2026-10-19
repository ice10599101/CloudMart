package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetAlbumAsset;
import com.cloudmart.pet.feign.FileFeignClient;
import com.cloudmart.pet.repository.PetAlbumAssetMapper;
import com.cloudmart.pet.repository.PetDiaryEntryMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMemoryMapper;
import com.cloudmart.pet.repository.PetNotifyPrefMapper;
import com.cloudmart.pet.repository.PetOnboardingProgressMapper;
import com.cloudmart.pet.service.PetUserGuardService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetAlbumPublicationTest {

    @Mock private PetMapper petMapper;
    @Mock private PetOnboardingProgressMapper onboardingMapper;
    @Mock private PetDiaryEntryMapper diaryMapper;
    @Mock private PetAlbumAssetMapper albumMapper;
    @Mock private PetMemoryMapper memoryMapper;
    @Mock private PetInventoryMapper inventoryMapper;
    @Mock private PetProperties properties;
    @Mock private PetNotifyPrefMapper notifyPrefMapper;
    @Mock private PetUserGuardService guardService;
    @Mock private FileFeignClient fileFeignClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private PetMetrics metrics;

    private PetCompanionFeatureService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetAlbumAsset.class);
    }

    @BeforeEach
    void setUp() {
        service = new PetCompanionFeatureService(petMapper, onboardingMapper, diaryMapper,
                albumMapper, memoryMapper, inventoryMapper, properties, notifyPrefMapper,
                guardService, fileFeignClient, transactionTemplate, metrics);
    }

    @Test
    void ownerCanMakeBoundLegacyPendingAssetPublicWithoutManualApproval() {
        PetAlbumAsset asset = asset("PENDING");
        when(albumMapper.selectById(88L)).thenReturn(asset);
        when(albumMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        PetAlbumAsset updated = service.updateAlbumAsset(100L, 5L, 88L, null, "PUBLIC", 1);

        assertThat(updated).isSameAs(asset);
        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<PetAlbumAsset>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(albumMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("visibility");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains("PUBLIC");
    }

    @Test
    void platformRejectedAssetCannotBeMadePublic() {
        when(albumMapper.selectById(88L)).thenReturn(asset("REJECTED"));

        assertThatThrownBy(() -> service.updateAlbumAsset(100L, 5L, 88L, null, "PUBLIC", 1))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasMessageContaining("平台限制");

        verify(albumMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    private static PetAlbumAsset asset(String auditStatus) {
        PetAlbumAsset asset = new PetAlbumAsset();
        asset.setId(88L);
        asset.setUserId(100L);
        asset.setPetId(5L);
        asset.setAuditStatus(auditStatus);
        asset.setBindStatus("BOUND");
        asset.setVisibility("OWNER_ONLY");
        asset.setVersion(1);
        return asset;
    }
}
