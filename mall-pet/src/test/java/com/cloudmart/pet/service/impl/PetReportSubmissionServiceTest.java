package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.feign.UserFeignClient;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetReportMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R05 举报创建加固测试（§7.2）：参数/目标核验、未结案同人同对象幂等（配额不消耗）、
 * 每日配额闸门、reportDate 填充、并发撞键幂等收敛与配额回退。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetReportSubmissionService 举报创建")
class PetReportSubmissionServiceTest {

    private static final Long USER = 17L;
    private static final Long TARGET_ID = 9001L;

    @Mock
    private PetReportMapper reportMapper;
    @Mock
    private PetWallMessageMapper wallMessageMapper;
    @Mock
    private PetBottleRecordMapper bottleRecordMapper;
    @Mock
    private PetQuotaService quotaService;
    @Mock
    private UserFeignClient userFeignClient;

    private PetReportSubmissionService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetReport.class);
        TableInfoHelper.initTableInfo(assistant, PetWallMessage.class);
        TableInfoHelper.initTableInfo(assistant, PetBottleRecord.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        PetClock petClock = new PetClock(Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC),
                properties);
        service = new PetReportSubmissionService(reportMapper, wallMessageMapper, bottleRecordMapper,
                quotaService, properties, petClock, userFeignClient);
        lenient().when(wallMessageMapper.selectById(anyLong())).thenReturn(new PetWallMessage());
        lenient().when(quotaService.tryConsume(any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        lenient().when(reportMapper.insert(any(PetReport.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PetReport.class).setId(7777L);
            return 1;
        });
    }

    private PetReport openReport() {
        PetReport report = new PetReport();
        report.setId(5555L);
        report.setReporterUserId(USER);
        report.setTargetType("WALL_MESSAGE");
        report.setTargetId(TARGET_ID);
        report.setStatus("PENDING");
        return report;
    }

    @Test
    @DisplayName("参数非法直接拒绝且不消耗配额")
    void rejectsInvalidArgumentsWithoutQuota() {
        assertThatThrownBy(() -> service.create(USER, "SOMETHING_ELSE", TARGET_ID, "理由", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(USER, "WALL_MESSAGE", null, "理由", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(USER, "WALL_MESSAGE", TARGET_ID, "  ", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(USER, "WALL_MESSAGE", TARGET_ID, "理由", "x".repeat(1001)))
                .isInstanceOf(BusinessException.class);
        verify(quotaService, never()).tryConsume(any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("目标不存在拒绝（留言墙本地核验）")
    void rejectsMissingWallTarget() {
        when(wallMessageMapper.selectById(TARGET_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.create(USER, "WALL_MESSAGE", TARGET_ID, "理由", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
        verify(reportMapper, never()).insert(any(PetReport.class));
    }

    @Test
    @DisplayName("同用户同目标未结案幂等：返回既有举报且不消耗配额")
    void dedupesOpenReportWithoutQuota() {
        PetReport existing = openReport();
        when(reportMapper.selectOne(any())).thenReturn(existing);
        PetReportSubmissionService.ReportSummary summary =
                service.create(USER, "WALL_MESSAGE", TARGET_ID, "理由", null);
        assertThat(summary.reportId()).isEqualTo(5555L);
        assertThat(summary.deduped()).isTrue();
        assertThat(summary.status()).isEqualTo("PENDING");
        verify(quotaService, never()).tryConsume(any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(reportMapper, never()).insert(any(PetReport.class));
    }

    @Test
    @DisplayName("配额耗尽拒绝入队")
    void rejectsWhenDailyQuotaExhausted() {
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(quotaService.tryConsume(any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.create(USER, "WALL_MESSAGE", TARGET_ID, "理由", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_QUOTA_EXHAUSTED);
        verify(reportMapper, never()).insert(any(PetReport.class));
    }

    @Test
    @DisplayName("正常创建：reportDate 填充、PENDING、isAuto=0")
    void createsReportWithBusinessDate() {
        when(reportMapper.selectOne(any())).thenReturn(null);
        PetReportSubmissionService.ReportSummary summary =
                service.create(USER, "WALL_MESSAGE", TARGET_ID, "刷屏广告", "补充说明");
        assertThat(summary.reportId()).isEqualTo(7777L);
        assertThat(summary.deduped()).isFalse();
        org.mockito.ArgumentCaptor<PetReport> captor = org.mockito.ArgumentCaptor.forClass(PetReport.class);
        verify(reportMapper).insert(captor.capture());
        PetReport inserted = captor.getValue();
        assertThat(inserted.getReportDate()).isEqualTo(java.time.LocalDate.parse("2026-10-05"));
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getIsAuto()).isZero();
        assertThat(inserted.getDescription()).isEqualTo("补充说明");
    }

    @Test
    @DisplayName("并发撞键：释放配额后幂等返回既有举报")
    void convergesToExistingOnDuplicateKey() {
        PetReport existing = openReport();
        // 预检未见 → insert 撞 uk_pet_report_open → 复查命中既有举报
        when(reportMapper.selectOne(any())).thenReturn(null, existing);
        when(reportMapper.insert(any(PetReport.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_pet_report_open"));
        PetReportSubmissionService.ReportSummary summary =
                service.create(USER, "WALL_MESSAGE", TARGET_ID, "理由", null);
        assertThat(summary.deduped()).isTrue();
        assertThat(summary.reportId()).isEqualTo(5555L);
        verify(quotaService).release(USER, PetQuotaService.QuotaType.REPORT, 0L);
    }

    @Test
    @DisplayName("我的举报不透出内部处理人字段")
    void listMineOmitsInternalFields() {
        PetReport report = openReport();
        report.setReason("刷屏广告");
        report.setStatus("HANDLED");
        report.setHandleAction("CONTENT_REMOVED");
        report.setHandleReason("已下架");
        report.setHandledBy(1L);
        when(reportMapper.selectList(any())).thenReturn(List.of(report));
        List<PetReportSubmissionService.ReportMineVO> mine = service.listMine(USER);
        assertThat(mine).hasSize(1);
        PetReportSubmissionService.ReportMineVO vo = mine.get(0);
        assertThat(vo.handleAction()).isEqualTo("CONTENT_REMOVED");
        assertThat(vo.handleReason()).isEqualTo("已下架");
        // ReportMineVO 不含 handledBy 字段：结构上不可能透出
        assertThat(PetReportSubmissionService.ReportMineVO.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("handledBy");
    }

    @Test
    @DisplayName("NICKNAME 目标在用户服务降级时放行（Fail-Open）")
    void nicknameTargetDegradesOpen() {
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(userFeignClient.batchGetUsers(any())).thenThrow(new RuntimeException("mall-user down"));
        PetReportSubmissionService.ReportSummary summary =
                service.create(USER, "NICKNAME", TARGET_ID, "昵称违规", null);
        assertThat(summary.deduped()).isFalse();
        assertThat(summary.reportId()).isEqualTo(7777L);
    }

    @Test
    @DisplayName("NICKNAME 目标在用户名单明确不含目标时拒绝")
    void nicknameTargetRejectedWhenAbsentFromRoster() {
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(userFeignClient.batchGetUsers(any()))
                .thenReturn(ApiResponse.ok(List.of(Map.of("id", 42, "nickname", "别人"))));
        assertThatThrownBy(() -> service.create(USER, "NICKNAME", TARGET_ID, "昵称违规", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
    }
}
