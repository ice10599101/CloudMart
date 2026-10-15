package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminCategorySaveRequest;
import com.cloudmart.software.dto.software.AdminItemQuery;
import com.cloudmart.software.dto.software.AdminTagSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.entity.SoftwareCategory;
import com.cloudmart.software.entity.SoftwareTag;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.AdminSoftwareCatalogMapper;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwarePlatformActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端目录/封禁/精选/分类标签单测（A10 独立封禁、首页精选 P0、10.5 分类标签维护）：
 * 与举报处置共用的平台处置状态机、CAS 版本冲突、DELETE=禁用而非物理删除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSoftwareCatalogServiceImplTest {

    private static final Long ADMIN_ID = 1000L;
    private static final Long SOFTWARE_ID = 100L;

    @Mock
    private AdminSoftwareCatalogMapper catalogMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareCategoryMapper categoryMapper;
    @Mock
    private SoftwareTagMapper tagMapper;
    @Mock
    private SoftwareAuditLogMapper auditLogMapper;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private OutboxService outboxService;

    private SoftwarePlatformActionService platformActionService;
    private AdminSoftwareCatalogServiceImpl catalogService;

    @BeforeEach
    void setUp() {
        // operationService 透传业务动作（幂等语义由 SoftwareOperationServiceTest 覆盖）
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(6)).get())
                .when(operationService).execute(anyString(), any(), anyString(), anyString(),
                        any(), any(), any(Supplier.class), any());
        platformActionService = new SoftwarePlatformActionService(softwareMapper, auditLogMapper,
                outboxService, new ObjectMapper(), fixedClock());
        catalogService = new AdminSoftwareCatalogServiceImpl(catalogMapper, softwareMapper,
                categoryMapper, tagMapper, auditLogMapper, operationService, platformActionService,
                fixedClock());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault());
    }

    private Software software(SoftwareLifecycle lifecycle, int lockVersion) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(42L);
        software.setLifecycle(lifecycle);
        software.setLockVersion(lockVersion);
        software.setFeaturedRank(0);
        return software;
    }

    // ------------------------------------------------------------------
    // 目录查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("目录查询：status 白名单校验，非法值直接拒绝")
    void itemPage_rejectsInvalidStatus() {
        AdminItemQuery query = new AdminItemQuery();
        query.setStatus("WHATEVER");
        assertThatThrownBy(() -> catalogService.itemPage(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("目录查询：ID 输出为字符串，待审快照 ID 一并返回")
    void itemPage_mapsRows() {
        AdminItemQuery query = new AdminItemQuery();
        AdminSoftwareCatalogMapper.ItemRow row = new AdminSoftwareCatalogMapper.ItemRow(
                SOFTWARE_ID, 42L, "ACTIVE", 3, 7, 2, 900L, 901L, 902L, "示例软件", "简介",
                5L, "效率工具", "FREE", 1, 2, 3L, 0, 0,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 2, 0, 0),
                LocalDateTime.of(2026, 9, 30, 0, 0), LocalDateTime.of(2026, 10, 3, 0, 0));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminSoftwareCatalogMapper.ItemRow>
                rowPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 1);
        rowPage.setRecords(List.of(row));
        when(catalogMapper.pageItems(any(), any())).thenReturn(rowPage);

        var result = catalogService.itemPage(query);

        assertThat(result.getTotal()).isEqualTo(1);
        Map<String, Object> item = result.getRecords().get(0);
        assertThat(item.get("softwareId")).isEqualTo("100");
        assertThat(item.get("ownerId")).isEqualTo("42");
        assertThat(item.get("lifecycle")).isEqualTo("ACTIVE");
        assertThat(item.get("featuredRank")).isEqualTo(3);
        assertThat(item.get("pendingRevisionId")).isEqualTo("902");
        assertThat(item.get("publishedRevisionId")).isEqualTo("900");
    }

    // ------------------------------------------------------------------
    // 封禁 / 解除（与举报处置共用状态机）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A10：独立封禁置 BANNED，写审计并通知分享者")
    void ban_setsBannedWithAuditAndEvent() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 3));

        assertThat(catalogService.ban(ADMIN_ID, SOFTWARE_ID, "ban-key-1", "侵权内容", 3)).isTrue();

        ArgumentCaptor<Software> captor = ArgumentCaptor.forClass(Software.class);
        verify(softwareMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycle()).isEqualTo(SoftwareLifecycle.BANNED);
        assertThat(captor.getValue().getLockVersion()).isEqualTo(4);
        ArgumentCaptor<SoftwareAuditLog> auditCaptor = ArgumentCaptor.forClass(SoftwareAuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("BAN");
        verify(outboxService).record(any(EventEnvelope.class));
    }

    @Test
    @DisplayName("封禁：expectedVersion 不匹配返回 SOFTWARE_VERSION_CONFLICT")
    void ban_versionConflict() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 5));

        assertThatThrownBy(() ->
                catalogService.ban(ADMIN_ID, SOFTWARE_ID, "ban-key-2", "原因", 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
        verify(softwareMapper, never()).updateById(any(Software.class));
    }

    @Test
    @DisplayName("封禁：已封禁重复调用幂等，不重复写事件")
    void ban_alreadyBannedIdempotent() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.BANNED, 6));

        assertThat(catalogService.ban(ADMIN_ID, SOFTWARE_ID, "ban-key-3", "原因", 6)).isTrue();

        verify(softwareMapper, never()).updateById(any(Software.class));
        verify(outboxService, never()).record(any(EventEnvelope.class));
    }

    @Test
    @DisplayName("解除封禁：BANNED → UNLISTED（由作者恢复展示）")
    void unban_returnsToUnlisted() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.BANNED, 8));

        assertThat(catalogService.unban(ADMIN_ID, SOFTWARE_ID, "unban-key-1", "核实无违规", 8))
                .isTrue();

        ArgumentCaptor<Software> captor = ArgumentCaptor.forClass(Software.class);
        verify(softwareMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycle()).isEqualTo(SoftwareLifecycle.UNLISTED);
    }

    @Test
    @DisplayName("解除封禁：非封禁状态返回 SOFTWARE_STATE_CONFLICT")
    void unban_nonBannedRejected() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 2));

        assertThatThrownBy(() ->
                catalogService.unban(ADMIN_ID, SOFTWARE_ID, "unban-key-2", "原因", 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封禁");
    }

    // ------------------------------------------------------------------
    // 精选排序
    // ------------------------------------------------------------------

    @Test
    @DisplayName("精选：设置 featured_rank 并写审计；相同值不重复写")
    void feature_setsRank() {
        Software software = software(SoftwareLifecycle.ACTIVE, 1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);

        assertThat(catalogService.feature(ADMIN_ID, SOFTWARE_ID, "feat-key-1", 3, 1))
                .isTrue();
        assertThat(software.getFeaturedRank()).isEqualTo(3);

        software.setLockVersion(2);
        catalogService.feature(ADMIN_ID, SOFTWARE_ID, "feat-key-2", 3, 2);
        // 相同值不重复写审计：两次调用合计只有 1 条
        verify(auditLogMapper, times(1)).insert(any(SoftwareAuditLog.class));
    }

    @Test
    @DisplayName("精选：rank 越界与版本冲突拒绝")
    void feature_rejectsInvalidInput() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 1));

        assertThatThrownBy(() ->
                catalogService.feature(ADMIN_ID, SOFTWARE_ID, "feat-key-3", -1, 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                catalogService.feature(ADMIN_ID, SOFTWARE_ID, "feat-key-4", 5, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
        verify(softwareMapper, never()).updateById(any(Software.class));
    }

    // ------------------------------------------------------------------
    // 分类 / 标签（DELETE=禁用）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分类：slug 冲突拒绝；更新既有分类不改 ID")
    void saveCategory_slugConflictAndUpdate() {
        SoftwareCategory existing = new SoftwareCategory();
        existing.setId(9L);
        existing.setSlug("tools");
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));

        AdminCategorySaveRequest request = new AdminCategorySaveRequest();
        request.setName("开发工具");
        request.setSlug("tools");
        assertThatThrownBy(() -> catalogService.saveCategory(ADMIN_ID, null, "cat-key-1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");

        // 更新自身不冲突
        when(categoryMapper.selectById(9L)).thenReturn(existing);
        assertThat(catalogService.saveCategory(ADMIN_ID, 9L, "cat-key-2", request)).isEqualTo(9L);
        verify(categoryMapper).updateById(existing);
    }

    @Test
    @DisplayName("分类：DELETE 为禁用（重复禁用幂等，不物理删除）")
    void disableCategory_isSoftDisable() {
        SoftwareCategory existing = new SoftwareCategory();
        existing.setId(9L);
        existing.setEnabled(true);
        when(categoryMapper.selectById(9L)).thenReturn(existing);

        assertThat(catalogService.disableCategory(ADMIN_ID, 9L, "cat-key-3")).isTrue();
        assertThat(existing.getEnabled()).isFalse();
        verify(categoryMapper, never()).deleteById(any(Long.class));

        catalogService.disableCategory(ADMIN_ID, 9L, "cat-key-4");
        verify(categoryMapper).updateById(existing);
    }

    @Test
    @DisplayName("标签：规范化后重复拒绝；新增返回 ID")
    void saveTag_normalizedConflict() {
        SoftwareTag existing = new SoftwareTag();
        existing.setId(7L);
        existing.setNormalizedName("工具");
        when(tagMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));

        AdminTagSaveRequest request = new AdminTagSaveRequest();
        request.setName(" 工具 ");
        assertThatThrownBy(() -> catalogService.saveTag(ADMIN_ID, null, "tag-key-1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");

        when(tagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        doAnswer(inv -> {
            ((SoftwareTag) inv.getArgument(0)).setId(8L);
            return 1;
        }).when(tagMapper).insert(any(SoftwareTag.class));
        AdminTagSaveRequest fresh = new AdminTagSaveRequest();
        fresh.setName("效率");
        assertThat(catalogService.saveTag(ADMIN_ID, null, "tag-key-2", fresh)).isEqualTo(8L);
    }

    @Test
    @DisplayName("标签：DELETE 为禁用")
    void disableTag_isSoftDisable() {
        SoftwareTag existing = new SoftwareTag();
        existing.setId(7L);
        existing.setEnabled(true);
        when(tagMapper.selectById(7L)).thenReturn(existing);

        assertThat(catalogService.disableTag(ADMIN_ID, 7L, "tag-key-3")).isTrue();
        assertThat(existing.getEnabled()).isFalse();
        verify(tagMapper, never()).deleteById(any(Long.class));
    }
}
