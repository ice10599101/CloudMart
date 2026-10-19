package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareDeclared;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareDeclaredMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * UX04-B 单测：对比 ID 数量/去重/可见性重校验；声明保存（null=清除为未提供）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareCompareServiceTest {

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareDeclaredMapper declaredMapper;

    private SoftwareCompareServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareDeclared.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareCompareServiceImpl(softwareMapper, revisionMapper,
                sourceMapper, releaseMapper, declaredMapper,
                Clock.fixed(LocalDateTime.of(2026, 10, 20, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private void mockVisible(long id) {
        Software software = new Software();
        software.setId(id);
        software.setPublishedRevisionId(100L + id);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        lenient().when(softwareMapper.selectById(id)).thenReturn(software);
        com.cloudmart.software.entity.SoftwareRevision revision =
                new com.cloudmart.software.entity.SoftwareRevision();
        revision.setId(100L + id);
        revision.setName("软件" + id);
        lenient().when(revisionMapper.selectById(100L + id)).thenReturn(revision);
    }

    @Test
    void compare_singleId_rejected() {
        assertThatThrownBy(() -> service.compare("7"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2~3");
    }

    @Test
    void compare_fourIds_rejected() {
        assertThatThrownBy(() -> service.compare("1,2,3,4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2~3");
    }

    @Test
    void compare_duplicateIds_deduped() {
        mockVisible(7L);
        mockVisible(8L);
        // 7,7,8 去重后 2 个 → 不报数量错误；7 二次出现不重复
        var items = service.compare("7,7,8");
        assertThat(items).hasSize(2);
    }

    @Test
    void compare_invisibleTarget_notFound() {
        mockVisible(7L);
        Software banned = new Software();
        banned.setId(8L);
        banned.setPublishedRevisionId(108L);
        banned.setLifecycle(SoftwareLifecycle.BANNED);
        lenient().when(softwareMapper.selectById(8L)).thenReturn(banned);
        lenient().when(revisionMapper.selectById(108L)).thenReturn(null); // 不可见目标在可见性检查处拒绝

        assertThatThrownBy(() -> service.compare("7,8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("软件不存在"); // 不泄露封禁状态
    }

    @Test
    void saveDeclared_null_clearsToNotProvided() {
        Software software = new Software();
        software.setId(7L);
        software.setPublishedRevisionId(107L);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        lenient().when(softwareMapper.selectById(7L)).thenReturn(software);
        lenient().when(declaredMapper.selectById(7L)).thenReturn(null);

        service.saveDeclared(7L, null, null, null, 1L, "k1");
        verify(declaredMapper).insert(any(SoftwareDeclared.class));

        var view = service.declaredOf(7L);
        assertThat(view.get("openSource")).isNull(); // 未提供
        assertThat(view.get("offlineCapable")).isNull();
    }
}
