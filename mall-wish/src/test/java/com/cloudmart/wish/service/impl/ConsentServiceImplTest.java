package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.entity.WishConsent;
import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.repository.WishConsentMapper;
import com.cloudmart.wish.vo.ConsentRecordVO;
import com.cloudmart.wish.vo.ConsentStatusVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConsentServiceImpl 单元测试。
 *
 * <p>覆盖：首次同意落库（哈希生成/截断）、重复提交幂等、并发唯一冲突兜底、
 * 撤回后状态判定（最新记录优先）、无记录默认未同意。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsentServiceImpl 单元测试")
class ConsentServiceImplTest {

    @Mock
    private WishConsentMapper wishConsentMapper;

    private ConsentServiceImpl consentService;

    private static final Long USER_ID = 1001L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishConsent.class);
    }

    @BeforeEach
    void setUp() {
        consentService = new ConsentServiceImpl(wishConsentMapper);
        when(wishConsentMapper.insert(any(WishConsent.class))).thenAnswer(invocation -> {
            WishConsent consent = invocation.getArgument(0);
            consent.setId(9001L);
            return 1;
        });
    }

    // ========== recordConsent ==========

    @Nested
    @DisplayName("recordConsent - 提交同意/撤回")
    class RecordTests {

        @Test
        @DisplayName("首次同意：落库 + 服务端生成 SHA-256 哈希 + IP/UA 截断")
        void shouldInsertWithGeneratedHash() {
            ConsentRecordVO vo = consentService.recordConsent(USER_ID,
                    new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", null, null),
                    "192.168.1.100", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)".repeat(20));

            assertThat(vo.id()).isEqualTo(9001L);
            assertThat(vo.consentType()).isEqualTo(ConsentType.AI_DATA_PROCESSING);
            assertThat(vo.version()).isEqualTo("v1.0");
            assertThat(vo.action()).isEqualTo(ConsentAction.GRANT);

            ArgumentCaptor<WishConsent> captor = ArgumentCaptor.forClass(WishConsent.class);
            verify(wishConsentMapper).insert(captor.capture());
            WishConsent saved = captor.getValue();
            assertThat(saved.getConsentTextHash()).hasSize(64).matches("[a-f0-9]+");
            assertThat(saved.getIp()).isEqualTo("192.168.1.100");
            assertThat(saved.getUserAgent()).hasSize(255);
        }

        @Test
        @DisplayName("客户端提供哈希：转小写后存储")
        void shouldStoreClientHashLowercase() {
            consentService.recordConsent(USER_ID,
                    new GrantConsentRequest(ConsentType.PRIVACY_POLICY, "v2.0",
                            ConsentAction.GRANT, "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"),
                    null, null);

            ArgumentCaptor<WishConsent> captor = ArgumentCaptor.forClass(WishConsent.class);
            verify(wishConsentMapper).insert(captor.capture());
            assertThat(captor.getValue().getConsentTextHash())
                    .isEqualTo("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        }

        @Test
        @DisplayName("重复提交相同 (类型,版本,动作)：幂等返回已有记录，不再插入")
        void shouldIdempotentlyReturnExisting() {
            WishConsent existing = buildConsent(ConsentAction.GRANT);
            when(wishConsentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

            ConsentRecordVO vo = consentService.recordConsent(USER_ID,
                    new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", null, null),
                    null, null);

            assertThat(vo.id()).isEqualTo(8001L);
            verify(wishConsentMapper, never()).insert(any(WishConsent.class));
        }

        @Test
        @DisplayName("并发唯一冲突：查询已有记录幂等返回")
        void shouldHandleConcurrentDuplicate() {
            WishConsent concurrent = buildConsent(ConsentAction.GRANT);
            when(wishConsentMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(null)   // 首次查无
                    .thenReturn(concurrent); // 冲突后查到
            when(wishConsentMapper.insert(any(WishConsent.class)))
                    .thenThrow(new DuplicateKeyException("uk_consent_unique"));

            ConsentRecordVO vo = consentService.recordConsent(USER_ID,
                    new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", null, null),
                    null, null);

            assertThat(vo.id()).isEqualTo(8001L);
        }

        @Test
        @DisplayName("并发唯一冲突且查询仍为空：返回提交失败错误")
        void shouldFailWhenConcurrentRecordMissing() {
            when(wishConsentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
            when(wishConsentMapper.insert(any(WishConsent.class)))
                    .thenThrow(new DuplicateKeyException("uk_consent_unique"));

            assertThatThrownBy(() -> consentService.recordConsent(USER_ID,
                    new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", null, null),
                    null, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }
    }

    // ========== getConsentStatus / hasGrantedAiDataProcessing ==========

    @Nested
    @DisplayName("getConsentStatus - 状态判定")
    class StatusTests {

        @Test
        @DisplayName("无任何记录：granted=false 且各字段为 null")
        void shouldReturnNotGrantedWhenNoRecord() {
            when(wishConsentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            ConsentStatusVO status = consentService.getConsentStatus(USER_ID, ConsentType.AI_DATA_PROCESSING);

            assertThat(status.granted()).isFalse();
            assertThat(status.version()).isNull();
            assertThat(status.latestAction()).isNull();
            assertThat(status.updatedAt()).isNull();
        }

        @Test
        @DisplayName("最新记录为 WITHDRAW：granted=false")
        void shouldReturnNotGrantedAfterWithdraw() {
            WishConsent withdraw = buildConsent(ConsentAction.WITHDRAW);
            when(wishConsentMapper.selectOne(any(Wrapper.class))).thenReturn(withdraw);

            assertThat(consentService.getConsentStatus(USER_ID, ConsentType.AI_DATA_PROCESSING).granted())
                    .isFalse();
            assertThat(consentService.hasGrantedAiDataProcessing(USER_ID)).isFalse();
        }

        @Test
        @DisplayName("最新记录为 GRANT：granted=true")
        void shouldReturnGrantedAfterGrant() {
            when(wishConsentMapper.selectOne(any(Wrapper.class))).thenReturn(buildConsent(ConsentAction.GRANT));

            ConsentStatusVO status = consentService.getConsentStatus(USER_ID, ConsentType.AI_DATA_PROCESSING);
            assertThat(status.granted()).isTrue();
            assertThat(consentService.hasGrantedAiDataProcessing(anyLong())).isTrue();
        }
    }

    private WishConsent buildConsent(ConsentAction action) {
        WishConsent consent = new WishConsent();
        consent.setId(8001L);
        consent.setUserId(USER_ID);
        consent.setConsentType(ConsentType.AI_DATA_PROCESSING);
        consent.setVersion("v1.0");
        consent.setAction(action);
        consent.setCreatedAt(LocalDateTime.now());
        return consent;
    }
}
