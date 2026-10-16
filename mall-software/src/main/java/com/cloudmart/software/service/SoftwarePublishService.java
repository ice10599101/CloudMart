package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.vo.software.SoftwareCreatedVO;
import com.cloudmart.software.vo.software.SoftwareEditorVO;
import com.cloudmart.software.vo.software.SoftwareMineItemVO;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;

/**
 * 发布与快照流程（方案 4.1/7.3/7.5）：create/working-revision/save/submit/
 * withdraw/unlist/relist/delete/editor/mine + 管理员审核决策。
 * 受限写入强制应用 SoftwarePublisherPolicy（4.0 矩阵）；
 * 创建/克隆/提交/审核走 SoftwareOperationService 幂等；关键流转写审计日志。
 */
public interface SoftwarePublishService {

    /** 创建软件（幂等）：ACTIVE 主体 + 初始 DRAFT 工作快照 */
    SoftwareCreatedVO create(Long userId, String idempotencyKey);

    /** 作者侧可选联系入口（仅启用项；ADMIN_PAID 查看条件的选择来源） */
    java.util.List<java.util.Map<String, Object>> listEnabledAccessContacts();

    /** 克隆工作快照（幂等）：已有工作副本直接返回；否则克隆 published（仅本人） */
    SoftwareEditorVO workingRevision(Long userId, Long softwareId, String idempotencyKey);

    /** 本人完整工作数据（含链接/提取码/审核原因；独立鉴权，与公开 DTO 隔离） */
    SoftwareEditorVO editor(Long userId, Long softwareId);

    /** 保存草稿（资格矩阵：是）：仅 DRAFT/REJECTED；expectedVersion CAS；全量替换子项 */
    SoftwareEditorVO saveDraft(Long userId, Long softwareId, Long revisionId,
                               Integer expectedVersion, SoftwareSnapshotInput input);

    /** 提交审核（资格+开关）：先远程审核绑定结果，再短事务冻结 PENDING，返回 submitSeq */
    SoftwareSubmittedVO submit(Long userId, Long softwareId, Long revisionId,
                               Integer expectedVersion, String idempotencyKey);

    /** 撤回待审（归属校验，不要求资格）：PENDING→DRAFT */
    SoftwareEditorVO withdraw(Long userId, Long softwareId, Long revisionId, Integer expectedVersion);

    /** 作者下架（归属校验）：ACTIVE→UNLISTED */
    SoftwareEditorVO unlist(Long userId, Long softwareId, Integer expectedVersion);

    /** 重新上架（资格矩阵：是）：UNLISTED→ACTIVE，须有已审快照；BANNED 拒绝 */
    SoftwareEditorVO relist(Long userId, Long softwareId, Integer expectedVersion);

    /** 软删除本人软件（归属校验；审计保留） */
    void delete(Long userId, Long softwareId, Integer expectedVersion);

    /** 管理员审核决策（幂等）：携带 revisionId+submitSeq+expectedVersion 防旧批次覆盖（A07） */
    SoftwareReviewDecisionVO reviewDecision(Long adminId, String idempotencyKey, Long softwareId,
                                            Long revisionId, Integer submitSeq, Integer expectedVersion,
                                            boolean approve, String reason, String requestId);

    /**
     * 管理端任意快照完整内容（含链接/提取码等秘密字段，仅审核域可读）：
     * 审核中心并列展示线上版与待审版的数据源；快照必须属于该软件。
     */
    SoftwareEditorVO revisionSnapshot(Long softwareId, Long revisionId);

    /** 我的软件（tab=published/draft/pending/rejected/favorites；subscriptions 为 P1） */
    Page<SoftwareMineItemVO> mine(Long userId, String tab, int page, int size);
}
