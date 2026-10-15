package com.cloudmart.user.service;

import com.cloudmart.user.vo.software.SoftwareProfileVO;

import java.util.List;

/**
 * 小答软件域用户资料适配（方案 9.6）：复用现有批量查询与脱敏策略，
 * 只返回昵称/头像/账号可用性；查询失败必须拒绝（软件侧展示降级为通用头像），
 * 绝不返回虚构资料。
 */
public interface SoftwareSupportService {

    /** 批量查询最小资料；结果仅含存在的 ID，缺失 ID 由调用方识别 */
    List<SoftwareProfileVO> profiles(List<Long> userIds);
}
