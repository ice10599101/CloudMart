package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SourceAppealCreateRequest;
import com.cloudmart.software.dto.software.SourceAppealCreatedVO;
import com.cloudmart.software.dto.software.SourceAppealHandleRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSourceAppeal;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceAppealMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SF08 补批：作者来源申诉。不变量——仅已发布软件的处置来源可申诉、
 * 处置快照落库、同一来源仅一条 PENDING、处理结论只落一次、ACCEPTED 恢复 UNKNOWN 而非 AVAILABLE。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareSourceAppealServiceTest {

    private static final Long SOFTWARE_ID = 5L;
    private static final Long REVISION_ID = 700L;
    private static final Long USER_ID = 11L;
    private static final Long ADMIN_ID = 99L;
    private static final Long APPEAL_ID = 7L;
    private static final Long DISPOSITION_ID = 900L;
    private static final String SOURCE_KEY = "00000000-0000-0000-0000-000000000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 30);

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareSourceAppealMapper appealMapper;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private AdminSoftwareSourceService adminSoftwareSourceService;
    @Mock
    private SoftwareAuditService auditService;

    private SoftwareSourceAppealService service;

    @BeforeAll
    static void initTableInfo() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        for (Class<?> type : new Class<?>[]{
                Software.class, SoftwareRevision.class,
                SoftwareSourceState.class, SoftwareSourceAppeal.class}) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareSourceAppealService(softwareMapper, revisionMapper,
                sourceStateMapper, appealMapper, operationService, adminSoftwareSourceService,
                auditService, new ObjectMapper(),
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()));

        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(USER_ID);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setPublishedRevisionId(REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);

        SoftwareSourceState state = new SoftwareSourceState();
        state.setId(DISPOSITION_ID);
        state.setSoftwareId(SOFTWARE_ID);
        state.setSourceKey(SOURCE_KEY);
        state.setAvailability(SourceAvailability.INVALID);
        state.setReason("链接返回 404");
        state.setCheckedAt(NOW.minusDays(1));
        when(sourceStateMapper.selectOne(any())).thenReturn(state);

        lenient().when(operationService.execute(anyString(), any(), anyString(), any(),
                        any(), any(), any(Supplier.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(6)).get());

        doAnswer(invocation -> {
            SoftwareSourceAppeal appeal = invocation.getArgument(0);
            appeal.setId(APPEAL_ID);
            return 1;
        }).when(appealMapper).insert(any(SoftwareSourceAppeal.class));
        doNothing().when(auditService).record(any(), any(), any(), anyString(), anyString(),
                any(), anyString(), any(), any());
    }

    private SourceAppealCreateRequest createRequest(String reason) {
        SourceAppealCreateRequest request = new SourceAppealCreateRequest();
        request.setSourceKey(SOURCE_KEY);
        request.setReason(reason);
        request.setEvidence("2026-10-20 复测链接可正常下载");
        return request;
    }

    private SoftwareSourceAppeal appeal(String status) {
        SoftwareSourceAppeal appeal = new SoftwareSourceAppeal();
        appeal.setId(APPEAL_ID);
        appeal.setSoftwareId(SOFTWARE_ID);
        appeal.setSourceKey(SOURCE_KEY);
        appeal.setDispositionId(DISPOSITION_ID);
        appeal.setDispositionAvailability("INVALID");
        appeal.setAppellantId(USER_ID);
        appeal.setStatus(status);
        return appeal;
    }

    private SourceAppealHandleRequest handleRequest(String decision, String note) {
        SourceAppealHandleRequest request = new SourceAppealHandleRequest();
        request.setDecision(decision);
        request.setNote(note);
        return request;
    }

    @Test
    @DisplayName("提交申诉：落 PENDING 并保存处置快照，审计关联处置 ID")
    void submit_persistsPendingWithDispositionSnapshot() {
        SourceAppealCreatedVO result = service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接已恢复，申请重新核实该来源"), "key-1");

        assertThat(result.appealId()).isEqualTo(APPEAL_ID);
        assertThat(result.status()).isEqualTo("PENDING");

        ArgumentCaptor<SoftwareSourceAppeal> captor =
                ArgumentCaptor.forClass(SoftwareSourceAppeal.class);
        verify(appealMapper).insert(captor.capture());
        SoftwareSourceAppeal saved = captor.getValue();
        assertThat(saved.getSoftwareId()).isEqualTo(SOFTWARE_ID);
        assertThat(saved.getSourceKey()).isEqualTo(SOURCE_KEY);
        assertThat(saved.getDispositionId()).isEqualTo(DISPOSITION_ID);
        assertThat(saved.getDispositionAvailability()).isEqualTo("INVALID");
        assertThat(saved.getDispositionReason()).isEqualTo("链接返回 404");
        assertThat(saved.getDispositionAt()).isEqualTo(NOW.minusDays(1));
        assertThat(saved.getAppellantId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo("PENDING");

        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(USER_ID), eq("USER"),
                eq("SOURCE_APPEAL"), eq("INVALID"), eq("PENDING"),
                contains("appealId=" + APPEAL_ID), isNull());
    }

    @Test
    @DisplayName("提交申诉：来源无可申诉处置（AVAILABLE）拒绝")
    void submit_rejectsNonAppealableDisposition() {
        SoftwareSourceState available = new SoftwareSourceState();
        available.setId(DISPOSITION_ID);
        available.setAvailability(SourceAvailability.AVAILABLE);
        when(sourceStateMapper.selectOne(any())).thenReturn(available);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接已恢复，申请重新核实该来源"), "key-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_SOURCE_APPEAL_INVALID");
        verify(appealMapper, never()).insert(any(SoftwareSourceAppeal.class));
    }

    @Test
    @DisplayName("提交申诉：未发布软件与越权访问统一返回 SOFTWARE_NOT_FOUND/状态冲突")
    void submit_rejectsUnpublishedOrForeignSoftware() {
        Software unpublished = new Software();
        unpublished.setId(SOFTWARE_ID);
        unpublished.setOwnerId(USER_ID);
        unpublished.setPublishedRevisionId(null);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(unpublished);
        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接已恢复，申请重新核实该来源"), "key-3"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_STATE_CONFLICT");

        Software foreign = new Software();
        foreign.setId(SOFTWARE_ID);
        foreign.setOwnerId(USER_ID + 1);
        foreign.setPublishedRevisionId(REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(foreign);
        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接已恢复，申请重新核实该来源"), "key-4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_NOT_FOUND");
    }

    @Test
    @DisplayName("提交申诉：同一来源已有 PENDING（唯一键）映射为 SOFTWARE_APPEAL_EXISTS")
    void submit_mapsDuplicatePendingToBusinessCode() {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_source_appeal_pending"))
                .when(appealMapper).insert(any(SoftwareSourceAppeal.class));

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接已恢复，申请重新核实该来源"), "key-5"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_APPEAL_EXISTS");
    }

    @Test
    @DisplayName("提交申诉：理由不足 10 字拒绝")
    void submit_rejectsTooShortReason() {
        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID,
                createRequest("链接正常"), "key-6"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_SOURCE_APPEAL_INVALID");
    }

    @Test
    @DisplayName("处理 ACCEPTED：来源恢复 UNKNOWN（不直接判 AVAILABLE），审计留痕")
    void handle_acceptedResetsSourceToUnknown() {
        when(appealMapper.selectById(APPEAL_ID)).thenReturn(appeal("PENDING"));
        when(appealMapper.handle(eq(APPEAL_ID), eq("ACCEPTED"), eq(ADMIN_ID), anyString(), any()))
                .thenReturn(1);

        boolean result = service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("accepted", "复测通过，恢复待重检"));

        assertThat(result).isTrue();
        verify(adminSoftwareSourceService).applyState(eq(ADMIN_ID), eq(SOFTWARE_ID), eq(SOURCE_KEY),
                eq("UNKNOWN"), contains("申诉通过"));
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(ADMIN_ID), eq("ADMIN"),
                eq("SOURCE_APPEAL_ACCEPT"), eq("INVALID"), eq("ACCEPTED"),
                contains("appealId=" + APPEAL_ID), isNull());

        ArgumentCaptor<LocalDateTime> handledAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appealMapper).handle(eq(APPEAL_ID), eq("ACCEPTED"), eq(ADMIN_ID),
                eq("复测通过，恢复待重检"), handledAt.capture());
        assertThat(handledAt.getValue()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("处理 REJECTED：保留原处置，不做来源恢复")
    void handle_rejectedKeepsDisposition() {
        when(appealMapper.selectById(APPEAL_ID)).thenReturn(appeal("PENDING"));
        when(appealMapper.handle(eq(APPEAL_ID), eq("REJECTED"), eq(ADMIN_ID), anyString(), any()))
                .thenReturn(1);

        assertThat(service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("REJECTED", "复测仍为 404"))).isTrue();

        verify(adminSoftwareSourceService, never()).applyState(any(), any(), anyString(),
                anyString(), anyString());
        verify(auditService).record(any(), any(), eq(ADMIN_ID), eq("ADMIN"),
                eq("SOURCE_APPEAL_REJECT"), any(), eq("REJECTED"), any(), any());
    }

    @Test
    @DisplayName("重复处理同一结论幂等；不同结论冲突拒绝")
    void handle_replayIdempotentAndConflictRejected() {
        when(appealMapper.selectById(APPEAL_ID)).thenReturn(appeal("ACCEPTED"));
        assertThat(service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("ACCEPTED", "复测通过"))).isTrue();
        verify(appealMapper, never()).handle(any(), anyString(), any(), anyString(), any());

        when(appealMapper.selectById(APPEAL_ID)).thenReturn(appeal("REJECTED"));
        assertThatThrownBy(() -> service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("ACCEPTED", "复测通过")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_STATE_CONFLICT");
    }

    @Test
    @DisplayName("并发处理 CAS 落空：若已被同结论处理则幂等返回，否则冲突")
    void handle_casLoserReReadsForIdempotency() {
        when(appealMapper.selectById(APPEAL_ID))
                .thenReturn(appeal("PENDING"), appeal("ACCEPTED"));
        when(appealMapper.handle(eq(APPEAL_ID), eq("ACCEPTED"), eq(ADMIN_ID), anyString(), any()))
                .thenReturn(0);

        assertThat(service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("ACCEPTED", "复测通过"))).isTrue();
        verify(adminSoftwareSourceService, never()).applyState(any(), any(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("处理参数：非法 decision 与空说明拒绝")
    void handle_rejectsInvalidPayload() {
        assertThatThrownBy(() -> service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("PENDING", "说明")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_SOURCE_APPEAL_INVALID");
        assertThatThrownBy(() -> service.handle(ADMIN_ID, APPEAL_ID,
                handleRequest("ACCEPTED", "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_SOURCE_APPEAL_INVALID");
        verify(appealMapper, never()).selectById(any());
    }
}
