package com.cloudmart.software.policy;

import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 4.2 公开可见谓词单测（S03 出口条件）：草稿/待审/退回/下架/封禁/删除一律不可见，
 * 指针异常（跨软件快照/未审快照）防御性拒绝。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareAccessPolicyTest {

    @Mock
    private SoftwareMapper softwareMapper;

    @Mock
    private SoftwareRevisionMapper softwareRevisionMapper;

    @InjectMocks
    private SoftwareAccessPolicy policy;

    private Software software(SoftwareLifecycle lifecycle, Long publishedRevisionId) {
        Software software = new Software();
        software.setId(100L);
        software.setOwnerId(7L);
        software.setLifecycle(lifecycle);
        software.setPublishedRevisionId(publishedRevisionId);
        return software;
    }

    private SoftwareRevision revision(Long softwareId, SoftwareReviewStatus status) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(501L);
        revision.setSoftwareId(softwareId);
        revision.setReviewStatus(status);
        return revision;
    }

    @Test
    @DisplayName("ACTIVE+已审指针可见；下架/封禁/删除/无指针均不可见")
    void visibleSoftware_lifecyclePredicate() {
        when(softwareMapper.selectById(100L)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 501L));
        assertThat(policy.visibleSoftware(100L)).isNotNull();

        when(softwareMapper.selectById(101L)).thenReturn(
                software(SoftwareLifecycle.UNLISTED, 501L));
        assertThat(policy.visibleSoftware(101L)).isNull();

        when(softwareMapper.selectById(102L)).thenReturn(
                software(SoftwareLifecycle.BANNED, 501L));
        assertThat(policy.visibleSoftware(102L)).isNull();

        Software deleted = software(SoftwareLifecycle.ACTIVE, 501L);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        when(softwareMapper.selectById(103L)).thenReturn(deleted);
        assertThat(policy.visibleSoftware(103L)).isNull();

        when(softwareMapper.selectById(104L)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null));
        assertThat(policy.visibleSoftware(104L)).isNull();

        assertThat(policy.visibleSoftware(null)).isNull();
        assertThat(policy.visibleSoftware(0L)).isNull();
    }

    @Test
    @DisplayName("公开快照：未审/退回状态不可见；跨软件指针防御性拒绝")
    void visiblePublishedRevision_guards() {
        when(softwareMapper.selectById(100L)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 501L));

        // 指针指向未审快照（脏数据防御）
        when(softwareRevisionMapper.selectById(501L)).thenReturn(revision(100L, SoftwareReviewStatus.PENDING));
        assertThat(policy.visiblePublishedRevision(100L)).isNull();

        // 指针指向其他软件的快照（ID 碰撞防御）
        when(softwareRevisionMapper.selectById(501L)).thenReturn(revision(999L, SoftwareReviewStatus.APPROVED));
        assertThat(policy.visiblePublishedRevision(100L)).isNull();

        // 正常：本软件已审快照
        when(softwareRevisionMapper.selectById(501L)).thenReturn(revision(100L, SoftwareReviewStatus.APPROVED));
        assertThat(policy.visiblePublishedRevision(100L)).isNotNull();
    }

    @Test
    @DisplayName("软件不可见时公开快照直接为 null（不二次泄漏）")
    void visiblePublishedRevision_invisibleSoftware() {
        when(softwareMapper.selectById(100L)).thenReturn(
                software(SoftwareLifecycle.UNLISTED, 501L));
        assertThat(policy.visiblePublishedRevision(100L)).isNull();
        assertThat(policy.visiblePublishedRevision(null)).isNull();
    }

    @Test
    @DisplayName("预览路径：revisionOfSoftware 只校验快照归属（归属鉴权由调用方执行）")
    void revisionOfSoftware_ownerScoped() {
        when(softwareRevisionMapper.selectById(501L)).thenReturn(revision(100L, SoftwareReviewStatus.DRAFT));
        assertThat(policy.revisionOfSoftware(501L, 100L)).isNotNull();
        assertThat(policy.revisionOfSoftware(501L, 999L)).isNull();
        assertThat(policy.revisionOfSoftware(null, 100L)).isNull();
    }

    @Test
    @DisplayName("作者视角：isOwnedLive 排除已删实体，保留 UNLISTED/BANNED 的整理能力")
    void isOwnedLive_lifecycleMatrix() {
        assertThat(policy.isOwnedLive(software(SoftwareLifecycle.ACTIVE, null), 7L)).isTrue();
        assertThat(policy.isOwnedLive(software(SoftwareLifecycle.UNLISTED, null), 7L)).isTrue();
        assertThat(policy.isOwnedLive(software(SoftwareLifecycle.BANNED, null), 7L)).isTrue();

        Software deleted = software(SoftwareLifecycle.DELETED, null);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        assertThat(policy.isOwnedLive(deleted, 7L)).isFalse();
        assertThat(policy.isOwnedLive(software(SoftwareLifecycle.ACTIVE, null), 8L)).isFalse();
        assertThat(policy.isOwnedLive(null, 7L)).isFalse();
    }
}
