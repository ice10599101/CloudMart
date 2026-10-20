package com.cloudmart.software.service;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * SW04：审核结果落库（上线/拒绝）的唯一实现。
 *
 * <p>提交冻结后的自动检查、人工审核与直接发布共用同一段事务内状态迁移，
 * 避免异步检查复制一套指针切换逻辑造成口径分叉。调用方负责持有软件/快照行锁
 * 并保证事务边界；本服务不做鉴权与入参校验。</p>
 */
@Service
@RequiredArgsConstructor
public class SoftwareApprovalService {

    private static final int SCHEMA_VERSION = 1;

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareAuditService auditService;
    private final OutboxService outboxService;
    private final SoftwareReleaseFanoutService fanoutService;
    private final SoftwareSnapshotAssembler assembler;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 审核通过：同事务切换 published 指针、清空 working、序号+1、Outbox/fanout。 */
    public SoftwareReviewDecisionVO publishApproved(Software locked, SoftwareRevision revision,
            Long actorId, String actorType, String action, String fromStatus,
            String reason, String requestId) {
        Long softwareId = locked.getId();
        Long revisionId = revision.getId();
        revision.setReviewStatus(SoftwareReviewStatus.APPROVED);
        revisionMapper.updateById(revision);
        // 事务内切换指针（4.1.3）：published→本快照、working 清空、上线序号+1
        int previousSeq = locked.getPublicationSeq() == null ? 0 : locked.getPublicationSeq();
        // SF05：业务变化判定需要与"上一线上快照"比较——指针切换前捕获
        Long previousRevisionId = locked.getPublishedRevisionId();
        locked.setPublishedRevisionId(revisionId);
        locked.setWorkingRevisionId(null);
        locked.setPublicationSeq(previousSeq + 1);
        if (locked.getPublishedAt() == null) {
            locked.setPublishedAt(LocalDateTime.now(clock));
        }
        locked.setLastReleaseAt(LocalDateTime.now(clock));
        // ⚠️ 必须用显式 UpdateWrapper 写 null：updateById 默认 NOT_NULL 策略会跳过
        // working_revision_id=null，导致指针滞留在已审快照上——已发布内容从此无法
        // 克隆编辑（A06/A07 回归，真实环境复现）
        softwareMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<Software>()
                .eq(Software::getId, softwareId)
                .set(Software::getPublishedRevisionId, revisionId)
                .set(Software::getWorkingRevisionId, null)
                .set(Software::getPublicationSeq, locked.getPublicationSeq())
                .set(Software::getPublishedAt, locked.getPublishedAt())
                .set(Software::getLastReleaseAt, locked.getLastReleaseAt()));
        auditService.record(softwareId, revisionId, actorId, actorType,
                action, fromStatus, "APPROVED", reason, requestId);
        // Outbox 与指针切换同事务（9.4）：软件事件载荷不含链接/提取码/正文
        outboxService.record(EventEnvelope.of(
                SoftwareEvents.TAG_REVIEW_APPROVED, SCHEMA_VERSION,
                String.valueOf(softwareId), locked.getPublicationSeq(), requestId,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(softwareId),
                        "revisionId", String.valueOf(revisionId),
                        "authorId", String.valueOf(locked.getOwnerId()),
                        "publicationSeq", String.valueOf(locked.getPublicationSeq()),
                        "directPublish", "DIRECT_PUBLISH".equals(action)))));
        // P1 更新订阅（9.4）：非首次上线才广播；事件与 fanout 任务同事务登记
        if (previousSeq > 0) {
            outboxService.record(EventEnvelope.of(
                    SoftwareEvents.TAG_RELEASE_PUBLISHED, SCHEMA_VERSION,
                    String.valueOf(softwareId), locked.getPublicationSeq(), requestId,
                    objectMapper.writeValueAsString(Map.of(
                            "softwareId", String.valueOf(softwareId),
                            "revisionId", String.valueOf(revisionId),
                            "publicationSeq", String.valueOf(locked.getPublicationSeq()),
                            "authorId", String.valueOf(locked.getOwnerId())))));
            fanoutService.registerTask(softwareId, revisionId, locked.getPublicationSeq(),
                    locked.getLastReleaseAt(), previousRevisionId);
        }
        return new SoftwareReviewDecisionVO(softwareId, revisionId, "APPROVED");
    }

    /**
     * 审核拒绝：旧提交记录内容保持不变，记录终态；克隆内容相同的新 DRAFT
     * 并切换 workingRevisionId（13.2.3 第 5 条）。拒绝记录通过 copiedFromRevisionId
     * 与可编辑副本关联，Mine 的 rejected 页签不因指针切换丢失原因。
     */
    public SoftwareRevision rejectAndClone(Software locked, SoftwareRevision revision,
            Long actorId, String actorType, String action, String fromStatus,
            String reason, String requestId) {
        Long softwareId = locked.getId();
        revision.setReviewStatus(SoftwareReviewStatus.REJECTED);
        revisionMapper.updateById(revision);
        auditService.record(softwareId, revision.getId(), actorId, actorType,
                action, fromStatus, "REJECTED", reason, requestId);

        SoftwareRevision draft = assembler.cloneInto(revision, softwareId, locked.getOwnerId());
        draft.setCopiedFromRevisionId(revision.getId());
        revisionMapper.insert(draft);
        assembler.cloneChildren(revision.getId(), draft.getId());
        locked.setWorkingRevisionId(draft.getId());
        softwareMapper.updateById(locked);
        auditService.record(softwareId, draft.getId(), actorId, actorType,
                "CLONE_REJECTED", "REJECTED", "DRAFT",
                "拒绝后生成可编辑副本 parentRevisionId=" + revision.getId(), requestId);
        return draft;
    }
}
