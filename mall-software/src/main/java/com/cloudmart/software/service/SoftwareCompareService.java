package com.cloudmart.software.service;

import java.util.List;
import java.util.Map;

/**
 * UX04-B：软件对比（2~3 款并排）。
 * 每次读取重校验可见性（非公开目标 404 语义）；缺失字段返回 null=未提供，
 * 不推断"支持/不支持"；价格只显示已知许可类别，不显示实时精确价格。
 */
public interface SoftwareCompareService {

    /** 对比数据（ids 逗号分隔；2~3 个去重 ID） */
    List<Map<String, Object>> compare(String ids);

    /** 管理读取结构化声明（含未提供） */
    Map<String, Object> declaredOf(Long softwareId);

    /** 管理保存声明（目标必须存在；null 字段=清除为未提供） */
    void saveDeclared(Long softwareId, Boolean openSource, Boolean offlineCapable,
                      String note, Long adminUserId, String idempotencyKey);
}
