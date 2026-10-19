package com.cloudmart.software.service;

import com.cloudmart.software.dto.software.AdminHomeSectionSaveRequest;

import java.util.List;
import java.util.Map;

/**
 * 首页内容运营配置（UX01）：固定区块 + 区块条目。
 * kind/data_source 白名单；MANUAL 条目与区块同事务替换；
 * 公共聚合实时过滤不可见目标；单块聚合失败不拖垮其他块。
 */
public interface SoftwareHomeSectionService {

    /** 公共首页聚合：enabled+投放期内+平台匹配；每块独立失败协议 */
    List<Map<String, Object>> publicHome(String platform);

    /** 管理列表（含未启用；编辑视图） */
    List<Map<String, Object>> adminSections();

    /** 预览：含未启用内容（只对管理员可读） */
    List<Map<String, Object>> preview(String platform);

    /** 新增/更新（sectionId 空=新增）；MANUAL 条目同事务全量替换；返回区块 ID */
    Long saveSection(Long sectionId, AdminHomeSectionSaveRequest request, String idempotencyKey);

    /** 启停 */
    boolean toggle(Long sectionId, boolean enabled, String idempotencyKey);

    /** 删除（软删除 + 条目清理） */
    boolean delete(Long sectionId, String idempotencyKey);

    /** 原子重排：完整集合校验（缺失/重复拒绝） */
    boolean reorder(List<Long> sectionIds);
}
