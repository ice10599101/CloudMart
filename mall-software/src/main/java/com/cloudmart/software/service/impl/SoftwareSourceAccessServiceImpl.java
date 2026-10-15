package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.SoftwareLinkAccessDaily;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.policy.SoftwareLinkAccessPolicy;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareLinkAccessDailyMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwareSourceAccessService;
import com.cloudmart.software.vo.software.SoftwareAccessPolicyVO;
import com.cloudmart.software.vo.software.SoftwareAccessStateVO;
import com.cloudmart.software.vo.software.SoftwareResolveVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 来源访问实现（方案 5.2/5.3/6.9）。
 *
 * <p>resolve 校验链（禁止仅凭 sourceId 取链接）：软件可见 → source→release→revision
 * 归属链必须落在当前线上快照（旧快照来源 SOFTWARE_SOURCE_STALE）→ 来源可用性
 * （DISABLED/INVALID 拒绝，UNKNOWN/SUSPECTED 可获取带提示）→ SoftwareLinkAccessPolicy
 * 三模式裁决 → 日获取去重事实（上海自然日唯一键；首插成功才原子递增累计计数；
 * 发布者本人不计）。远程等级裁决在事务外，计数双写在短事务内。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareSourceAccessServiceImpl implements SoftwareSourceAccessService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareLinkAccessPolicy linkAccessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareLinkAccessDailyMapper linkAccessDailyMapper;
    private final SoftwareAccessContactMapper accessContactMapper;
    private final SoftwareRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SoftwareResolveVO resolve(Long userId, Long softwareId, Long sourceId) {
        // 公开可见谓词（游客已在 SecurityConfig 拦截；纵深防御再核）
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 11.2 限频：单用户每分钟获取链接上限（Redis 降级放行，不参与查看条件裁决）
        if (!rateLimiter.allowResolve(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "获取链接过于频繁，请稍后再试");
        }
        SourceRelease sourceRelease = loadSourceInPublishedSnapshot(published, sourceId);

        // 来源可用性（4.3 指纹匹配；DISABLED 无条件）
        SoftwareSourceState state = sourceStateMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getSoftwareId, softwareId)
                        .eq(SoftwareSourceState::getSourceKey, sourceRelease.source().getSourceKey()));
        String availability = displayAvailability(sourceRelease.source(), state);
        if (availability.equals(SourceAvailability.DISABLED.name())
                || availability.equals(SourceAvailability.INVALID.name())) {
            throw new BusinessException("SOFTWARE_SOURCE_UNAVAILABLE",
                    "该来源已失效或被禁用，请选择其他来源");
        }

        // 查看条件三模式裁决（远程等级查询在事务外；不返回源信息的拒绝带明确原因码）
        SoftwareLinkAccessPolicy.LinkAccessDecision decision =
                linkAccessPolicy.decide(userId, published, softwareId);
        if (!decision.allowed()) {
            throw new BusinessException(decision.reasonCode(), denialMessage(decision.reasonCode()));
        }

        // 日获取去重计数（6.9）：同用户同来源同上海自然日一次；发布者本人不计
        boolean counted = false;
        if (!userId.equals(loadOwnerId(softwareId))) {
            counted = recordDailyAccess(softwareId, sourceRelease.source().getSourceKey(), userId);
        }

        return buildResolveVo(sourceRelease.source(), availability);
    }

    /** 来源归属链：source→release→当前线上 revision；旧快照来源 409（5.2） */
    private SourceRelease loadSourceInPublishedSnapshot(SoftwareRevision published, Long sourceId) {
        SoftwareSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        SoftwareRelease release = releaseMapper.selectById(source.getReleaseId());
        if (release == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (!release.getRevisionId().equals(published.getId())) {
            // 版本被新快照替换：旧 sourceId 无效，前端刷新来源列表重新选择（A14）
            throw new BusinessException("SOFTWARE_SOURCE_STALE", "来源列表已更新，请刷新后重新选择");
        }
        return new SourceRelease(source, release);
    }

    private record SourceRelease(SoftwareSource source, SoftwareRelease release) {
    }

    /** 日获取事实与累计计数同短事务：首插成功才计数；当日重复获取静默不计数 */
    private boolean recordDailyAccess(Long softwareId, String sourceKey, Long userId) {
        LocalDate today = LocalDate.now(SHANGHAI);
        Boolean inserted = transactionTemplate.execute(status -> {
            SoftwareLinkAccessDaily daily = new SoftwareLinkAccessDaily();
            daily.setSoftwareId(softwareId);
            daily.setSourceKey(sourceKey);
            daily.setUserId(userId);
            daily.setAccessDate(today);
            try {
                linkAccessDailyMapper.insert(daily);
            } catch (DuplicateKeyException e) {
                // 同用户同来源同日已计：复合主键去重是业务热度唯一口径（6.9）
                return false;
            }
            softwareMapper.incrementLinkAccessCount(softwareId);
            return true;
        });
        return Boolean.TRUE.equals(inserted);
    }

    private Long loadOwnerId(Long softwareId) {
        Software software = softwareMapper.selectById(softwareId);
        return software == null ? null : software.getOwnerId();
    }

    private String displayAvailability(SoftwareSource source, SoftwareSourceState state) {
        if (state == null) {
            return SourceAvailability.UNKNOWN.name();
        }
        if (state.getAvailability() == SourceAvailability.DISABLED) {
            return SourceAvailability.DISABLED.name();
        }
        if (state.getCheckedFingerprint() != null
                && state.getCheckedFingerprint().equals(source.getContentFingerprint())
                && state.getAvailability() != null) {
            return state.getAvailability().name();
        }
        return SourceAvailability.UNKNOWN.name();
    }

    private SoftwareResolveVO buildResolveVo(SoftwareSource source, String availability) {
        return new SoftwareResolveVO(source.getId(), source.getSourceKey(),
                source.getSourceType().name(), source.getProvider().name(),
                source.getPlatform().name(), source.getArch().name(), source.getLabel(),
                source.getUrl(), source.getExtractionCode(), source.getArchivePassword(),
                source.getSizeLabel(), availability, availabilityTip(availability));
    }

    /** 5.1：语法校验不宣传为安全认证；UNKNOWN/SUSPECTED 如实提示 */
    private String availabilityTip(String availability) {
        return switch (availability) {
            case "SUSPECTED" -> "有用户反馈该来源可能失效，正在核实中；如无法访问请选择其他来源或反馈";
            case "UNKNOWN" -> "该来源暂无最新核实信息，请以实际访问为准";
            default -> null;
        };
    }

    private String denialMessage(String reasonCode) {
        return switch (reasonCode) {
            case "SOFTWARE_LEVEL_REQUIRED" -> "当前社区等级未达到该软件的查看门槛";
            case "SOFTWARE_PAYMENT_ACCESS_REQUIRED" -> "该软件需管理员确认付款后查看，请联系管理员";
            case "SOFTWARE_NOT_FOUND" -> "软件不存在";
            default -> "暂无权限查看该来源";
        };
    }

    @Override
    public SoftwareAccessStateVO accessState(Long userId, Long softwareId) {
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        SoftwareLinkAccessPolicy.LinkAccessDecision decision =
                linkAccessPolicy.decide(userId, published, softwareId);
        List<SoftwareAccessPolicyVO.ContactVO> contacts =
                published.getAccessMode() == SoftwareAccessMode.ADMIN_PAID
                        ? accessContactMapper.selectList(new LambdaQueryWrapper<SoftwareAccessContact>()
                                .eq(SoftwareAccessContact::getEnabled, true)
                                .orderByAsc(SoftwareAccessContact::getSortOrder))
                        .stream()
                        .map(contact -> new SoftwareAccessPolicyVO.ContactVO(contact.getId(),
                                contact.getName(), contact.getContactType(),
                                contact.getContactValue()))
                        .toList()
                        : List.of();
        return new SoftwareAccessStateVO(softwareId,
                published.getAccessMode() == null ? null : published.getAccessMode().name(),
                decision.allowed(), decision.reasonCode(), decision.currentLevel(),
                decision.requiredLevel(), decision.grantExpiresAt(), contacts);
    }
}
