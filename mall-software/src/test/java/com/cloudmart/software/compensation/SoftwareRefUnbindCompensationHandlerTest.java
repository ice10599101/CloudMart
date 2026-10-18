package com.cloudmart.software.compensation;

import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SW08：解绑补偿执行前必须核对当前有效引用——
 * 旧操作的过期意图不得撤销后续成功保存的绑定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRefUnbindCompensationHandlerTest {

    private static final long REVISION_ID = 501L;
    private static final long ASSET_ID = 9L;

    @Mock
    private SoftwareFileFeignClient fileFeignClient;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareRevisionMediaMapper revisionMediaMapper;

    private SoftwareRefUnbindCompensationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SoftwareRefUnbindCompensationHandler(
                fileFeignClient, revisionMapper, revisionMediaMapper, new ObjectMapper());
    }

    private String payload() {
        return "{\"assetId\":" + ASSET_ID + ",\"bizType\":\"SOFTWARE_REVISION\","
                + "\"bizId\":\"" + REVISION_ID + "\",\"operationId\":\"SAVE-501-abc\",\"kind\":\"rollback\"}";
    }

    @Test
    @DisplayName("资产仍被媒体行引用 → 跳过解绑（不得撤销后续成功保存的绑定）")
    void skipWhenStillReferencedByMedia() {
        when(revisionMediaMapper.selectCount(any())).thenReturn(1L);

        handler.execute(String.valueOf(REVISION_ID), payload());

        verify(fileFeignClient, never()).unbindReference(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("资产仍被图标引用 → 跳过解绑")
    void skipWhenStillReferencedByIcon() {
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setIconAssetId(ASSET_ID);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision);

        handler.execute(String.valueOf(REVISION_ID), payload());

        verify(fileFeignClient, never()).unbindReference(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("当前内容不再引用 → 执行解绑（引用不存在亦幂等成功）")
    void unbindWhenNotReferenced() {
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setIconAssetId(777L);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision);

        handler.execute(String.valueOf(REVISION_ID), payload());

        verify(fileFeignClient).unbindReference(eq(ASSET_ID), eq("SOFTWARE_REVISION"),
                eq(String.valueOf(REVISION_ID)));
    }
}
