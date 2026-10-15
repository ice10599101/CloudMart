package com.cloudmart.software.policy;

import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.repository.SoftwarePublisherPermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 4.0 发布资格矩阵单测：默认拒绝（记录缺失=false 均拒绝）、fail-closed、
 * 受限写入与审核通过强制校验。
 */
@ExtendWith(MockitoExtension.class)
class SoftwarePublisherPolicyTest {

    @Mock
    private SoftwarePublisherPermissionMapper publisherPermissionMapper;

    @Spy
    private SoftwareProperties softwareProperties = new SoftwareProperties();

    @InjectMocks
    private SoftwarePublisherPolicy policy;

    private SoftwarePublisherPermission permission(boolean canPublish) {
        SoftwarePublisherPermission permission = new SoftwarePublisherPermission();
        permission.setId(1L);
        permission.setUserId(42L);
        permission.setCanPublish(canPublish);
        permission.setUpdatedBy(1000L);
        permission.setReason("测试开通");
        permission.setLockVersion(0);
        permission.setUpdatedAt(LocalDateTime.now());
        return permission;
    }

    @Test
    @DisplayName("无权限记录 → 拒绝（A31：默认 403，直接 API 也无法绕过）")
    void decision_missingRecordDenied() {
        when(publisherPermissionMapper.selectOne(any())).thenReturn(null);

        var decision = policy.decision(42L);

        assertThat(decision.canPublish()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SOFTWARE_PUBLISH_NOT_ENABLED");
    }

    @Test
    @DisplayName("canPublish=false → 拒绝（A33：管理员关闭后受限写入被拒）")
    void decision_disabledDenied() {
        when(publisherPermissionMapper.selectOne(any())).thenReturn(permission(false));

        assertThat(policy.decision(42L).canPublish()).isFalse();
    }

    @Test
    @DisplayName("canPublish=true → 放行并携带权限行（lockVersion 供并发控制）")
    void decision_enabledAllowed() {
        SoftwarePublisherPermission record = permission(true);
        when(publisherPermissionMapper.selectOne(any())).thenReturn(record);

        var decision = policy.decision(42L);

        assertThat(decision.canPublish()).isTrue();
        assertThat(decision.permission()).isSameAs(record);
    }

    @Test
    @DisplayName("非法 userId → 拒绝")
    void decision_invalidUserDenied() {
        assertThat(policy.decision(null).canPublish()).isFalse();
        assertThat(policy.decision(0L).canPublish()).isFalse();
    }

    @Test
    @DisplayName("受限写入强制校验：未开通抛 SOFTWARE_PUBLISH_NOT_ENABLED（403）")
    void requireForRestrictedWrite_throwsWhenDenied() {
        when(publisherPermissionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> policy.requireForRestrictedWrite(42L))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_NOT_ENABLED");
    }

    @Test
    @DisplayName("审核通过同样要求作者仍有发布资格（A33：资格关闭后不得通过）")
    void requireForReviewApproval_enforcesMatrix() {
        when(publisherPermissionMapper.selectOne(any())).thenReturn(permission(false));

        assertThatThrownBy(() -> policy.requireForReviewApproval(42L))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_NOT_ENABLED");
    }

    @Test
    @DisplayName("uiHint 仅供 UI 提示（写接口仍重查）；携带全站 publish-enabled 开关")
    void uiHint_reflectsState() {
        when(publisherPermissionMapper.selectOne(any())).thenReturn(permission(true));

        var hint = policy.uiHint(42L);

        assertThat(hint.canPublish()).isTrue();
        assertThat(hint.reasonCode()).isNull();
        // SoftwareProperties 默认 publish-enabled=false（上线前全关）
        assertThat(hint.publishEnabled()).isFalse();
    }
}
