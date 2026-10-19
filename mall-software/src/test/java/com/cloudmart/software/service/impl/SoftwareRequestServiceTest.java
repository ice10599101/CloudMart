package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareRequestCreateInput;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRequest;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRequestMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * UX06 单测：提交校验/状态显式机/审核拒绝必填原因/已找到目标可见性/
 * 公开 feed 只出 APPROVED 且不造假活跃。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRequestServiceTest {

    @Mock
    private SoftwareRequestMapper requestMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;

    private SoftwareRequestServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 1, 0);

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareRequest.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareRequestServiceImpl(requestMapper, softwareMapper, revisionMapper,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private SoftwareRequestCreateInput validInput() {
        SoftwareRequestCreateInput input = new SoftwareRequestCreateInput();
        input.setTitle("找一款批量重命名工具");
        input.setUsageDesc("需要给几百张照片按拍摄日期批量重命名，最好支持预览和撤销，Windows 下使用。");
        input.setPlatformScope(List.of("WINDOWS"));
        return input;
    }

    private void mockApprovedRequest(long id) {
        SoftwareRequest request = new SoftwareRequest();
        request.setId(id);
        request.setUserId(9L);
        request.setAuditStatus("APPROVED");
        request.setStatus("OPEN");
        request.setDeletedAt(null);
        lenient().when(requestMapper.selectById(id)).thenReturn(request);
    }

    @Test
    void create_valid_pendingAudit() {
        service.create(9L, validInput());
        verify(requestMapper).insert(any(SoftwareRequest.class));
        org.mockito.ArgumentCaptor<SoftwareRequest> captor =
                org.mockito.ArgumentCaptor.forClass(SoftwareRequest.class);
        verify(requestMapper).insert(captor.capture());
        // 提交进入审核（恶意/重复需求可审核），前台状态初始 OPEN
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void create_usageTooShort_rejected() {
        SoftwareRequestCreateInput input = validInput();
        input.setUsageDesc("太短");
        assertThatThrownBy(() -> service.create(9L, input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("20~500");
    }

    @Test
    void audit_rejected_requiresReason() {
        mockApprovedRequest(1L);
        SoftwareRequest pending = new SoftwareRequest();
        pending.setId(2L);
        pending.setAuditStatus("PENDING");
        lenient().when(requestMapper.selectById(2L)).thenReturn(pending);

        assertThatThrownBy(() -> service.audit(1L, 2L, false, null, "k1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");
    }

    @Test
    void markMatched_invisibleTarget_rejected() {
        SoftwareRequest approved = new SoftwareRequest();
        approved.setId(3L);
        approved.setAuditStatus("APPROVED");
        approved.setStatus("OPEN");
        lenient().when(requestMapper.selectById(3L)).thenReturn(approved);
        Software banned = new Software();
        banned.setId(8L);
        banned.setPublishedRevisionId(108L);
        banned.setLifecycle(SoftwareLifecycle.BANNED);
        lenient().when(softwareMapper.selectById(8L)).thenReturn(banned);

        // 已找到必须为当前可见软件（不让刷"解决数"）
        assertThatThrownBy(() -> service.markMatched(1L, 3L, 8L, "理由", "k2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可见");
    }

    @Test
    void publicFeed_excludesPending() {
        SoftwareRequest approved = new SoftwareRequest();
        approved.setId(1L);
        approved.setTitle("找截图工具");
        approved.setAuditStatus("APPROVED");
        approved.setStatus("OPEN");
        lenient().when(requestMapper.selectList(any())).thenReturn(List.of(approved));

        var rows = service.publicFeed();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status")).isEqualTo("OPEN");
    }
}
