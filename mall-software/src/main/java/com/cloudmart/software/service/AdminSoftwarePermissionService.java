package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.AdminContactSaveRequest;
import com.cloudmart.software.dto.software.AdminGrantRequest;
import com.cloudmart.software.dto.software.AdminGrantRevokeRequest;
import com.cloudmart.software.dto.software.AdminPermissionToggleRequest;

import java.util.List;
import java.util.Map;

/**
 * 发布资格/付款授权/联系入口管理（方案 4.0/6.14）：全部走 operation(ADMIN) 幂等 +
 * software_permission_audit 同事务；资格开通先核对会员存在且可用（失败 503，
 * 不创建未知用户授权）。
 */
public interface AdminSoftwarePermissionService {

    /** 发布资格分页（mall-admin 侧拼装会员资料） */
    Page<Map<String, Object>> permissionPage(int page, int size);

    /** 批量查询指定会员资格（会员管理详情页用） */
    List<Map<String, Object>> permissionsOf(List<Long> userIds);

    /** 开通/关闭发布资格（唯一键首开幂等；数据库为最终事实；开通前核对会员可用） */
    boolean togglePermission(Long adminId, Long userId, AdminPermissionToggleRequest request);

    /** 某软件的付款授权列表 */
    List<Map<String, Object>> grantsOf(Long softwareId);

    /** 开通/延期授权（重开更新同一行并追加审计） */
    boolean grant(Long adminId, Long softwareId, AdminGrantRequest request);

    /** 撤销授权 */
    boolean revoke(Long adminId, Long softwareId, Long userId, AdminGrantRevokeRequest request);

    /** 联系入口列表（启用+停用） */
    List<Map<String, Object>> contacts();

    /** 新增/更新联系入口（引用中仅停用，不物理删除） */
    Long saveContact(Long adminId, AdminContactSaveRequest request);
}
