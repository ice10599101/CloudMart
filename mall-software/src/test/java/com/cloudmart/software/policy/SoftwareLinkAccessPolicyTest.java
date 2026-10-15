package com.cloudmart.software.policy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareLevelVO;
import com.cloudmart.software.entity.SoftwareAccessGrant;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessGrantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.4 三种查看条件裁决单测（A35/A36/A37/A40）：REGISTERED 登录即放行；
 * COMMUNITY_LEVEL 实时比对且异常 fail-closed；ADMIN_PAID 有效授权谓词
 * （按 software+user+ACTIVE+未过期 精确匹配，恰好到期即无效）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareLinkAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Long USER_ID = 42L;
    private static final Long SOFTWARE_ID = 100L;

    @Mock
    private CommunitySupportFeignClient communitySupportFeignClient;

    @Mock
    private SoftwareAccessGrantMapper accessGrantMapper;

    @Spy
    private final Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());

    private SoftwareLinkAccessPolicy policy;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SoftwareAccessGrant.class);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        policy = new SoftwareLinkAccessPolicy(communitySupportFeignClient, accessGrantMapper, clock);
    }

    private SoftwareRevision revision(SoftwareAccessMode mode, Integer minLevel) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(501L);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setAccessMode(mode);
        revision.setMinCommunityLevel(minLevel);
        return revision;
    }

    private SoftwareAccessGrant grant(LocalDateTime expiresAt) {
        SoftwareAccessGrant grant = new SoftwareAccessGrant();
        grant.setId(1L);
        grant.setSoftwareId(SOFTWARE_ID);
        grant.setUserId(USER_ID);
        grant.setStatus("ACTIVE");
        grant.setExpiresAt(expiresAt);
        return grant;
    }

    @Test
    @DisplayName("A35 REGISTERED：登录用户放行，不要求发布资格/等级/付款")
    void registeredMode_allowsLoggedInUser() {
        var decision = policy.decide(USER_ID, revision(SoftwareAccessMode.REGISTERED, null), SOFTWARE_ID);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isNull();
    }

    @Test
    @DisplayName("A36 等级模式：低于门槛拒绝并返回当前/要求等级；达到门槛放行")
    void communityLevel_comparesRealtime() {
        when(communitySupportFeignClient.getLevel(USER_ID)).thenReturn(
                ApiResponse.ok(new SoftwareLevelVO(USER_ID, 2, "活跃", NOW)));

        var denied = policy.decide(USER_ID, revision(SoftwareAccessMode.COMMUNITY_LEVEL, 3), SOFTWARE_ID);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo("SOFTWARE_LEVEL_REQUIRED");
        assertThat(denied.currentLevel()).isEqualTo(2);
        assertThat(denied.requiredLevel()).isEqualTo(3);

        when(communitySupportFeignClient.getLevel(USER_ID)).thenReturn(
                ApiResponse.ok(new SoftwareLevelVO(USER_ID, 3, "进阶", NOW)));
        var allowed = policy.decide(USER_ID, revision(SoftwareAccessMode.COMMUNITY_LEVEL, 3), SOFTWARE_ID);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    @DisplayName("A36 伪造 level 无效：只认社区返回事实；社区空响应/异常 fail-closed 503")
    void communityLevel_failureClosed() {
        when(communitySupportFeignClient.getLevel(USER_ID)).thenReturn(ApiResponse.ok(null));

        assertThatThrownBy(() -> policy.decide(
                USER_ID, revision(SoftwareAccessMode.COMMUNITY_LEVEL, 3), SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_SERVICE_UNAVAILABLE");

        when(communitySupportFeignClient.getLevel(USER_ID))
                .thenThrow(new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "down"));
        assertThatThrownBy(() -> policy.decide(
                USER_ID, revision(SoftwareAccessMode.COMMUNITY_LEVEL, 3), SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("A37 付款模式：无授权拒绝，不返回任何源信息")
    void adminPaid_deniedWithoutGrant() {
        when(accessGrantMapper.selectOne(any())).thenReturn(null);

        var decision = policy.decide(USER_ID, revision(SoftwareAccessMode.ADMIN_PAID, null), SOFTWARE_ID);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SOFTWARE_PAYMENT_ACCESS_REQUIRED");
    }

    @Test
    @DisplayName("A38/A40 有效授权放行并回传到期时间（长期有效为 null）；谓词按软件+用户+ACTIVE+未过期精确匹配")
    @SuppressWarnings("unchecked")
    void adminPaid_grantPredicate() {
        when(accessGrantMapper.selectOne(any(Wrapper.class))).thenReturn(grant(null));
        var permanent = policy.decide(USER_ID, revision(SoftwareAccessMode.ADMIN_PAID, null), SOFTWARE_ID);
        assertThat(permanent.allowed()).isTrue();
        assertThat(permanent.grantExpiresAt()).isNull();

        when(accessGrantMapper.selectOne(any(Wrapper.class)))
                .thenReturn(grant(LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()).plusDays(30)));
        var timed = policy.decide(USER_ID, revision(SoftwareAccessMode.ADMIN_PAID, null), SOFTWARE_ID);
        assertThat(timed.allowed()).isTrue();
        assertThat(timed.grantExpiresAt()).isNotNull();

        // 实质校验谓词：捕获 Wrapper 的 SQL 段与绑定参数，
        // 确认 (software_id, user_id, status=ACTIVE, expires_at OR NULL) 四组条件全部下推
        when(accessGrantMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        policy.decide(USER_ID, revision(SoftwareAccessMode.ADMIN_PAID, null), SOFTWARE_ID);
        ArgumentCaptor<Wrapper<SoftwareAccessGrant>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(accessGrantMapper, org.mockito.Mockito.atLeastOnce()).selectOne(captor.capture());
        LambdaQueryWrapper<SoftwareAccessGrant> wrapper =
                (LambdaQueryWrapper<SoftwareAccessGrant>) captor.getValue();
        String sqlSegment = wrapper.getSqlSegment().toLowerCase();
        assertThat(sqlSegment)
                .contains("software_id").contains("user_id")
                .contains("status").contains("expires_at")
                .contains(">");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(SOFTWARE_ID, USER_ID, "ACTIVE");
    }

    @Test
    @DisplayName("纵深防御：游客拒绝；无效快照拒绝；快照缺失门槛按拒绝处理")
    void decide_defensiveDenials() {
        assertThat(policy.decide(null, revision(SoftwareAccessMode.REGISTERED, null), SOFTWARE_ID)
                .reasonCode()).isEqualTo("UNAUTHORIZED");
        assertThat(policy.decide(USER_ID, null, SOFTWARE_ID).reasonCode())
                .isEqualTo("SOFTWARE_NOT_FOUND");

        SoftwareRevision broken = revision(SoftwareAccessMode.COMMUNITY_LEVEL, null);
        broken.setMinCommunityLevel(null);
        assertThat(policy.decide(USER_ID, broken, SOFTWARE_ID).reasonCode())
                .isEqualTo("SOFTWARE_LEVEL_REQUIRED");
    }
}
