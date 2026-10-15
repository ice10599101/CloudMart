package com.cloudmart.software.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 软件公开可见策略（方案 4.2）：统一供详情、评论列表、评论互动、评分、收藏、
 * 订阅、榜单、来源摘要、来源解析和分享使用。
 *
 * <p>公开可见谓词：{@code deleted_at IS NULL AND lifecycle=ACTIVE
 * AND published_revision_id IS NOT NULL AND publishedRevision.review_status=APPROVED}。</p>
 *
 * <p>作者/管理员预览走独立鉴权接口（编辑数据/后台详情），不得给公开详情接口加
 * "作者可查看所有状态"后被旁路复用——本策略只回答"公众能否看见"。</p>
 */
@Component
@RequiredArgsConstructor
public class SoftwareAccessPolicy {

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper softwareRevisionMapper;

    /** @return 公开可见的软件主体（未通过时 null，调用方统一按不存在处置，不泄露下架/封禁详情） */
    public Software visibleSoftware(Long softwareId) {
        if (softwareId == null || softwareId <= 0) {
            return null;
        }
        Software software = softwareMapper.selectById(softwareId);
        return isVisible(software) ? software : null;
    }

    /** @return 公开可见软件的当前已审快照（软件不可见或指针异常时 null） */
    public SoftwareRevision visiblePublishedRevision(Long softwareId) {
        Software software = visibleSoftware(softwareId);
        if (software == null || software.getPublishedRevisionId() == null) {
            return null;
        }
        SoftwareRevision revision = softwareRevisionMapper.selectById(software.getPublishedRevisionId());
        // 指针必须属于本软件且已审（防御脏数据）
        if (revision == null || !softwareId.equals(revision.getSoftwareId())
                || revision.getReviewStatus() != SoftwareReviewStatus.APPROVED) {
            return null;
        }
        return revision;
    }

    /** 纯谓词判断（已加载主体时避免二次查询） */
    public boolean isVisible(Software software) {
        return software != null
                && software.getDeletedAt() == null
                && software.getLifecycle() == SoftwareLifecycle.ACTIVE
                && software.getPublishedRevisionId() != null;
    }

    /** 软删除/lifecycle 检查（供作者本人视角：整理既有内容时排除已删实体） */
    public boolean isOwnedLive(Software software, Long ownerId) {
        return software != null
                && software.getDeletedAt() == null
                && software.getLifecycle() != SoftwareLifecycle.DELETED
                && ownerId.equals(software.getOwnerId());
    }

    /** 作者/管理员预览专用：不校验公开可见性，仅校验快照归属（调用方先行鉴权归属） */
    public SoftwareRevision revisionOfSoftware(Long revisionId, Long softwareId) {
        SoftwareRevision revision = softwareRevisionMapper.selectById(revisionId);
        if (revision == null || !softwareId.equals(revision.getSoftwareId())) {
            return null;
        }
        return revision;
    }

    /** 快照查询（软件内范围） */
    public SoftwareRevision revision(Long revisionId) {
        LambdaQueryWrapper<SoftwareRevision> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SoftwareRevision::getId, revisionId);
        return softwareRevisionMapper.selectOne(wrapper);
    }
}
