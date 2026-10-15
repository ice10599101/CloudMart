package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.AdminCategorySaveRequest;
import com.cloudmart.software.dto.software.AdminItemQuery;
import com.cloudmart.software.dto.software.AdminTagSaveRequest;

import java.util.List;
import java.util.Map;

/**
 * 管理端软件目录与分类标签（方案 7.5/10.1/10.5）：
 * 目录查询（线上/待审/精选/下架/封禁）、精选排序、分类与标签维护；
 * 封禁/解除由 {@link SoftwarePlatformActionService} 与举报处置共用同一实现。
 */
public interface AdminSoftwareCatalogService {

    /** 目录分页：keyword/分享者/分类/授权/状态；仅排除软删除行 */
    Page<Map<String, Object>> itemPage(AdminItemQuery query);

    /** 独立封禁：必须原因与版本；审计+通知分享者；重复封禁幂等 */
    boolean ban(Long adminId, Long softwareId, String idempotencyKey,
                String reason, Integer expectedVersion);

    /** 解除封禁：BANNED → UNLISTED（由作者恢复展示）；非封禁态返回冲突 */
    boolean unban(Long adminId, Long softwareId, String idempotencyKey,
                  String reason, Integer expectedVersion);

    /** 精选排序：featuredRank=0 取消精选；CAS 防覆盖；写审计 */
    boolean feature(Long adminId, Long softwareId, String idempotencyKey,
                    Integer featuredRank, Integer expectedVersion);

    /** 全部分类（含禁用，运营视图）；按 sortOrder、id 升序 */
    List<Map<String, Object>> categories();

    /** 新增/更新分类（categoryId 空=新增）；slug 唯一；返回分类 ID */
    Long saveCategory(Long adminId, Long categoryId, String idempotencyKey,
                      AdminCategorySaveRequest request);

    /** 禁用分类（DELETE 语义，不物理删除）；重复禁用幂等 */
    boolean disableCategory(Long adminId, Long categoryId, String idempotencyKey);

    /** 标签分页（含禁用），keyword 匹配标签名 */
    Page<Map<String, Object>> tags(String keyword, Integer page, Integer size);

    /** 新增/更新标签（tagId 空=新增）；规范化去重；返回标签 ID */
    Long saveTag(Long adminId, Long tagId, String idempotencyKey, AdminTagSaveRequest request);

    /** 禁用标签（DELETE 语义，不物理删除）；重复禁用幂等 */
    boolean disableTag(Long adminId, Long tagId, String idempotencyKey);
}
