package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.vo.software.SoftwareEditorVO;

import java.util.List;
import java.util.Map;

/**
 * 管理端查询与评论处置（方案 7.5/6.8）：仅 mall-admin 代理可达。
 */
public interface AdminSoftwareQueryService {

    /**
     * 待审列表（PENDING 快照 + 软件信息）。status 可选 PENDING/CHECKING，
     * 缺省 PENDING；CHECKING 行附带检查任务状态与最后错误，供后台运维只读查看。
     */
    Page<Map<String, Object>> pendingPage(String status, int page, int size);

    /**
     * 审核快照对比（方案 10.2）：目标快照完整内容 + 当前线上快照（可为 null=首次发布），
     * 前端据此标出链接域名、提取码、来源声明、正文与截图变化；快照快照含秘密字段，
     * 仅审核域可读，通用 OperLog 排除请求响应体。
     */
    Map<String, Object> reviewDetail(Long revisionId);

    /** 后台完整详情（独立鉴权可读秘密字段 + 最近审核历史） */
    Map<String, Object> adminDetail(Long softwareId);

    /** 评论管理列表（含 HIDDEN/PENDING/DELETED 状态） */
    Page<Map<String, Object>> commentPage(Long softwareId, String status, int page, int size);

    /**
     * 评论人工处置：APPROVE（PENDING→VISIBLE 计数+1+可见事件）/HIDE（VISIBLE→HIDDEN
     * 计数-1）/RESTORE（HIDDEN→VISIBLE 计数+1）/REJECT（PENDING→HIDDEN 不计数）。
     * 状态机与计数同事务，重复处置幂等（operation）。
     */
    boolean moderateComment(Long adminId, Long softwareId, Long commentId,
                            String idempotencyKey, String action, String reason);
}
