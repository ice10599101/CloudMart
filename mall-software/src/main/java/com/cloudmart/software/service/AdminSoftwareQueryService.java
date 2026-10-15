package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.vo.software.SoftwareEditorVO;

import java.util.List;
import java.util.Map;

/**
 * 管理端查询与评论处置（方案 7.5/6.8）：仅 mall-admin 代理可达。
 */
public interface AdminSoftwareQueryService {

    /** 待审列表（PENDING 快照 + 软件信息） */
    Page<Map<String, Object>> pendingPage(int page, int size);

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
