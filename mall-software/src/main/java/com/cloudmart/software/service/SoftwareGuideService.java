package com.cloudmart.software.service;

import com.cloudmart.software.dto.software.AdminGuideSaveRequest;

import java.util.List;
import java.util.Map;

/**
 * UX04-A：软件教程元数据（正文与审核真源在社区）。
 * 公开列表/详情实时校验文章公开状态——文章撤回立即从软件入口隐藏；
 * 首批仅运营创建教程；关联软件被封禁后不给出失效获取按钮。
 */
public interface SoftwareGuideService {

    /** 公开教程分页（type 筛选；enabled 且文章公开） */
    Map<String, Object> publicGuides(String guideType, int page, int size);

    /** 公开详情（含社区文章摘要/封面；文章非公开统一 404 语义） */
    Map<String, Object> publicGuideDetail(Long guideId);

    /**
     * 管理分页（含未启用/文章非公开/关联软件失效标注）。
     * maintenanceOnly=true 时仅返回关联软件已失效的教程（后台维护队列）。
     */
    Map<String, Object> adminGuides(String guideType, boolean maintenanceOnly, int page, int size);

    /** 新增/更新（guideId 空=新增）；元数据校验+articleId 公开状态预检；返回 ID */
    Long saveGuide(Long guideId, AdminGuideSaveRequest request, String idempotencyKey);

    /** 启停 */
    boolean toggle(Long guideId, boolean enabled, String idempotencyKey);

    /** 删除（软删除，幂等） */
    boolean delete(Long guideId, String idempotencyKey);
}
