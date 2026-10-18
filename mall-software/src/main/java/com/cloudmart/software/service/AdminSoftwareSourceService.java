package com.cloudmart.software.service;

import com.cloudmart.software.vo.software.AdminSoftwareSourceVO;

import java.util.List;

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

    /**
     * SW06：当前线上快照的来源清单（按版本/平台/架构），链接默认脱敏。
     *
     * @throws com.cloudmart.common.exception.BusinessException 软件不存在或无可管理线上内容
     */
    List<AdminSoftwareSourceVO> listSources(Long softwareId);
}
