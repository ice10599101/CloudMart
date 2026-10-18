package com.cloudmart.software.service.impl;

import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareMapper;
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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SW09：评论条件迁移——两个并发处置只有一个真正迁移，计数只扣一次。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareCommentStatusServiceImplTest {

    private static final Long SOFTWARE_ID = 101L;
    private static final Long COMMENT_ID = 1L;
    private static final Long ADMIN_ID = 9L;

    @Mock
    private SoftwareCommentMapper commentMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareAuditLogMapper auditLogMapper;

    private SoftwareCommentStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SoftwareCommentStatusServiceImpl(commentMapper, softwareMapper,
                auditLogMapper, Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"),
                ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("条件迁移成功：计数-1 且写审计（两个举报并发只扣一次）")
    void transitionSuccessAdjustsCountOnce() {
        when(commentMapper.transitionIfFrom(COMMENT_ID, "VISIBLE", "HIDDEN")).thenReturn(1);

        boolean migrated = service.transition(SOFTWARE_ID, COMMENT_ID,
                SoftwareCommentStatus.VISIBLE, SoftwareCommentStatus.HIDDEN,
                -1, ADMIN_ID, "COMMENT_HIDE", "违规");

        assertThat(migrated).isTrue();
        verify(softwareMapper).incrementCommentCount(SOFTWARE_ID, -1);
        verify(auditLogMapper).insert(any(com.cloudmart.software.entity.SoftwareAuditLog.class));
    }

    @Test
    @DisplayName("条件迁移失败（状态已被并发处置）：不调计数、不写审计")
    void transitionSkippedWhenAlreadyMigrated() {
        when(commentMapper.transitionIfFrom(COMMENT_ID, "VISIBLE", "HIDDEN")).thenReturn(0);

        boolean migrated = service.transition(SOFTWARE_ID, COMMENT_ID,
                SoftwareCommentStatus.VISIBLE, SoftwareCommentStatus.HIDDEN,
                -1, ADMIN_ID, "COMMENT_HIDE", "违规");

        assertThat(migrated).isFalse();
        verify(softwareMapper, never()).incrementCommentCount(any(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(auditLogMapper, never()).insert(any(com.cloudmart.software.entity.SoftwareAuditLog.class));
    }
}
