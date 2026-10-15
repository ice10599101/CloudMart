package com.cloudmart.software.policy;

import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.repository.SoftwarePublisherPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 发布资格策略（方案 4.0）：默认关闭，所有用户默认 canPublish=false，
 * 数据库无权限记录也按 false 处理；发布资格以数据库为最终事实，
 * 每次受限写入和审核通过都重新检查（缓存仅用于 UI 提示）。
 *
 * <p>权限检查失败不得因 Feign fallback 或缓存默认值而放行（fail-closed）。</p>
 */
@Component
@RequiredArgsConstructor
public class SoftwarePublisherPolicy {

    private final SoftwarePublisherPermissionMapper publisherPermissionMapper;
    private final SoftwareProperties softwareProperties;

    /** 资格结论（数据库实时读取，供受限写入口与审核侧强制应用） */
    public PublisherDecision decision(Long userId) {
        if (userId == null || userId <= 0) {
            return PublisherDecision.denied("INVALID_USER");
        }
        // 4.0：全站默认发布资格固定 false（publisher-default-enabled 不可配置绕过），
        // 此处读开关仅作为模块级停用信号，个人资格仍以权限行为准
        SoftwarePublisherPermission permission = publisherPermissionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwarePublisherPermission>()
                        .eq(SoftwarePublisherPermission::getUserId, userId));
        if (permission == null || !Boolean.TRUE.equals(permission.getCanPublish())) {
            return PublisherDecision.denied("SOFTWARE_PUBLISH_NOT_ENABLED");
        }
        return PublisherDecision.allowed(permission);
    }

    /**
     * 4.0 发布资格矩阵（受限写入类）：创建软件/克隆工作副本/保存草稿与版本来源/
     * 提交审核/重新上架都要求资格；关闭资格后服务端 403，直接调用 API 也不能绕过。
     */
    public void requireForRestrictedWrite(Long userId) {
        PublisherDecision decision = decision(userId);
        if (!decision.canPublish()) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "SOFTWARE_PUBLISH_NOT_ENABLED",
                    "暂未开通软件发布权限，请联系管理员");
        }
    }

    /**
     * 4.0 矩阵（审核通过类）：管理员审核通过用户投稿时要求作者仍有发布资格；
     * 资格关闭时不得通过（待审稿保留，可退回，重新开通后可继续审核）。
     */
    public void requireForReviewApproval(Long authorId) {
        requireForRestrictedWrite(authorId);
    }

    /**
     * 4.0 矩阵（非资格类，仅归属校验）：作者读取已有编辑数据/撤回待审/下架/删除本人软件
     * 不要求资格——保留整理既有内容的能力，但不能借此重新发布（写入另受矩阵约束）。
     */
    public boolean readOrLifecycleOnlyOperation(Long userId) {
        return userId != null && userId > 0;
    }

    /** publisher-status 端点用：只供 UI 提示，写接口仍重查 */
    public UiHint uiHint(Long userId) {
        PublisherDecision decision = decision(userId);
        return new UiHint(decision.canPublish(), decision.canPublish() ? null : decision.reasonCode(),
                softwareProperties.isPublishEnabled());
    }

    /**
     * @param canPublish  数据库实时资格结论
     * @param reasonCode  不可发布原因（UI 提示用）
     * @param publishEnabled 全站投稿开关（publish-enabled，控制提交/重新上架入口）
     */
    public record UiHint(boolean canPublish, String reasonCode, boolean publishEnabled) {
    }

    /** 资格裁决结果：canPublish + 原因码 + 权限行（lockVersion 供后台开关并发控制） */
    public record PublisherDecision(boolean canPublish, String reasonCode,
                                    SoftwarePublisherPermission permission) {

        static PublisherDecision allowed(SoftwarePublisherPermission permission) {
            return new PublisherDecision(true, null, permission);
        }

        static PublisherDecision denied(String reasonCode) {
            return new PublisherDecision(false, reasonCode, null);
        }
    }
}
