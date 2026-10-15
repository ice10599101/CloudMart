package com.cloudmart.software.policy;

import com.cloudmart.software.dto.software.SoftwareLevelVO;
import com.cloudmart.software.entity.SoftwareAccessGrant;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessGrantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 链接查看条件统一裁决（方案 5.4/7.3）：resolve、access-state、任何复制/批量查看/
 * 移动端适配都必须调用本策略，不得新增一个不带查看条件检查的"链接详情"接口。
 *
 * <p>规则（三种模式互斥）：</p>
 * <ul>
 *   <li>REGISTERED：登录且账号状态正常即放行（登录由调用方保证，resolve 强制登录）；</li>
 *   <li>COMMUNITY_LEVEL：每次 resolve 实时查询社区等级，只认社区返回的当前事实，
 *       不接受前端传入 level，不使用长期等级缓存；等级服务不可用必须拒绝
 *       （不能把查询异常当成达到门槛）；</li>
 *   <li>ADMIN_PAID：存在该用户对该软件的有效人工授权（ACTIVE 且未过期）；
 *       恰好到期即无效；付款授权不提升社区等级，也不绕过等级模式。</li>
 * </ul>
 *
 * <p>软件下架、来源禁用、账号封禁优先于任何授权（resolve 服务层先行校验）。
 * 账号状态由调用方经用户服务适配核对；本策略只裁决查看条件本身。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareLinkAccessPolicy {

    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final SoftwareAccessGrantMapper accessGrantMapper;
    private final Clock clock;

    /**
     * 统一裁决入口。
     *
     * @param userId   已验证登录用户（resolve 强制登录；游客在 Controller 层已被 401 拦截）
     * @param revision 当前线上快照（携带 accessMode/minCommunityLevel/accessContactId）
     * @param softwareId 软件ID（授权范围=一会员+一款软件）
     * @return 放行结论或明确原因码（不含任何源信息）
     */
    public LinkAccessDecision decide(Long userId, SoftwareRevision revision, Long softwareId) {
        if (userId == null || userId <= 0) {
            // 游客在网关/SecurityConfig 已拦；纵深防御再拒一次
            return LinkAccessDecision.denied("UNAUTHORIZED", null, null, null);
        }
        if (revision == null || revision.getAccessMode() == null) {
            // 无有效线上快照＝无可查看条件（调用方应先过 SoftwareAccessPolicy）
            return LinkAccessDecision.denied("SOFTWARE_NOT_FOUND", null, null, null);
        }
        return switch (revision.getAccessMode()) {
            case REGISTERED -> LinkAccessDecision.allowed(null, null, null);
            case COMMUNITY_LEVEL -> decideCommunityLevel(userId, revision);
            case ADMIN_PAID -> decideAdminPaid(userId, softwareId);
        };
    }

    /** 等级模式：实时查询，拒绝异常兜底；门槛取本次快照值，不缓存 */
    private LinkAccessDecision decideCommunityLevel(Long userId, SoftwareRevision revision) {
        Integer requiredLevel = revision.getMinCommunityLevel();
        if (requiredLevel == null || requiredLevel <= 0) {
            // 提交审核时已强制校验；防御脏数据按拒绝处理（fail-closed）
            return LinkAccessDecision.denied("SOFTWARE_LEVEL_REQUIRED", null, null, null);
        }
        SoftwareLevelVO level = communitySupportFeignClient.getLevel(userId).data();
        if (level == null || level.level() == null) {
            // 社区成功响应才可判定；空响应按依赖不可用拒绝，不当作达到/未达门槛
            throw new com.cloudmart.common.exception.BusinessException(
                    "COMMUNITY_SERVICE_UNAVAILABLE", "社区等级服务暂不可用，请稍后重试");
        }
        Integer currentLevel = level.level();
        if (currentLevel < requiredLevel) {
            return LinkAccessDecision.denied("SOFTWARE_LEVEL_REQUIRED",
                    currentLevel, requiredLevel, null);
        }
        return LinkAccessDecision.allowed(currentLevel, requiredLevel, null);
    }

    /** 付款模式：有效授权谓词（ACTIVE 且未过期，恰好到期即无效） */
    private LinkAccessDecision decideAdminPaid(Long userId, Long softwareId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        SoftwareAccessGrant grant = accessGrantMapper.selectOne(
                new LambdaQueryWrapper<SoftwareAccessGrant>()
                        .eq(SoftwareAccessGrant::getSoftwareId, softwareId)
                        .eq(SoftwareAccessGrant::getUserId, userId)
                        .eq(SoftwareAccessGrant::getStatus, "ACTIVE")
                        .and(wrapper -> wrapper.isNull(SoftwareAccessGrant::getExpiresAt)
                                .or()
                                .gt(SoftwareAccessGrant::getExpiresAt, now)));
        if (grant == null) {
            return LinkAccessDecision.denied("SOFTWARE_PAYMENT_ACCESS_REQUIRED", null, null, null);
        }
        return LinkAccessDecision.allowed(null, null, grant.getExpiresAt());
    }

    /**
     * @param allowed       是否放行（不含任何源信息）
     * @param reasonCode    不放行原因：UNAUTHORIZED/SOFTWARE_NOT_FOUND/
     *                      SOFTWARE_LEVEL_REQUIRED/SOFTWARE_PAYMENT_ACCESS_REQUIRED
     * @param currentLevel  等级模式当前等级（其他模式 null）
     * @param requiredLevel 等级模式要求等级
     * @param grantExpiresAt 付款授权到期时间（长期有效为 null）
     */
    public record LinkAccessDecision(boolean allowed, String reasonCode,
                                     Integer currentLevel, Integer requiredLevel,
                                     LocalDateTime grantExpiresAt) {

        static LinkAccessDecision allowed(Integer currentLevel, Integer requiredLevel,
                                          LocalDateTime grantExpiresAt) {
            return new LinkAccessDecision(true, null, currentLevel, requiredLevel, grantExpiresAt);
        }

        static LinkAccessDecision denied(String reasonCode, Integer currentLevel,
                                         Integer requiredLevel, LocalDateTime grantExpiresAt) {
            return new LinkAccessDecision(false, reasonCode, currentLevel, requiredLevel, grantExpiresAt);
        }
    }
}
