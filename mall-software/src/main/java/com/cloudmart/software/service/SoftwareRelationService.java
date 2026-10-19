package com.cloudmart.software.service;

import com.cloudmart.software.dto.software.AdminRelationSaveRequest;

import java.util.List;
import java.util.Map;

/**
 * 软件同类关系（UX03）：人工维护，不上黑盒推荐。
 * 全量替换语义（Items 详情管理）；公开聚合在查询侧实时过滤不可见目标。
 */
public interface SoftwareRelationService {

    /** 管理视图：全量关系（不过滤可见性，供编辑检查目标状态） */
    List<Map<String, Object>> listRelations(Long softwareId);

    /** 全量替换：校验自关联/目标存在且已发布/类型/reason/数量；返回替换后条数 */
    int saveRelations(Long softwareId, AdminRelationSaveRequest request);
}
