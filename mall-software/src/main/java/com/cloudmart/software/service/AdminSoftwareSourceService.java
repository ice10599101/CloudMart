package com.cloudmart.software.service;

/**
 * 管理员来源状态处置（方案 4.3/5.3）：核实结论独立于不可变快照，
 * 按 (software_id, source_key) upsert；平台禁用优先于用户反馈。
 */
public interface AdminSoftwareSourceService {

    /**
     * @return true=应用成功
     * @throws com.cloudmart.common.exception.BusinessException 软件不可见/来源键当前快照不存在
     *         （核实类）/状态值非法
     */
    boolean applyState(Long adminId, Long softwareId, String sourceKey,
                       String availability, String reason);
}
