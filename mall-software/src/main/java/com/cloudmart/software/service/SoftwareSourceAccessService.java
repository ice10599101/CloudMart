package com.cloudmart.software.service;

import com.cloudmart.software.vo.software.SoftwareAccessStateVO;
import com.cloudmart.software.vo.software.SoftwareResolveVO;

/**
 * 来源访问（方案 5.2）：resolve 统一入口与 access-state 查看条件判定。
 * resolve/任何复制、批量查看、移动端适配都必须经 SoftwareLinkAccessPolicy，
 * 不得新增不带查看条件检查的"链接详情"接口。
 */
public interface SoftwareSourceAccessService {

    /**
     * 获取下载信息：登录、软件可见、来源属于当前线上快照（stale 409）、
     * 未禁用/未失效、查看条件裁决、日获取去重计数（发布者本人不计）。
     * 响应经 Controller 设置 no-store。
     */
    SoftwareResolveVO resolve(Long userId, Long softwareId, Long sourceId);

    /** 查看条件判定结果（不返回真实来源；no-store） */
    SoftwareAccessStateVO accessState(Long userId, Long softwareId);
}
