package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.datascope.DataScopeResult;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.admin.service.DataScopeService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员授权策略（S03 集中化）：数据范围、内置超管保护、授权上限的唯一权威。
 *
 * <ul>
 *   <li>{@link #assertCanManage}：数据范围 + 超管目标保护——范围外/超管目标一律拒绝；</li>
 *   <li>{@link #assertNotBuiltInSuperAdmin}：删除/禁用类无条件保护（与既有 delete 语义一致）；</li>
 *   <li>{@link #assertNotLastEnabledSuperAdmin}：禁用时至少保留一名可用内置超管；</li>
 *   <li>{@link #assertGrantableMenus}/{@link #assertGrantableDataScope}：授权上限——
 *       普通管理员只能授予自身权限集范围内的菜单，不能创建/赋予 ALL 数据范围的角色
 *       （越级授权/自我提权路径关闭，QA21）。</li>
 * </ul>
 *
 * <p>操作者上下文取 {@link AdminSecurityContext}（网关强注入，不可伪造）；
 * 缺失上下文按最严格策略处理。</p>
 */
@Component
public class AdminAuthorizationPolicy {

    /** 内置超级管理员角色标识 */
    public static final String BUILT_IN_SUPER_ROLE_KEY = "admin";
    /** 数据范围：全部数据（admin_role.data_scope 字典码，1=全部） */
    public static final int DATA_SCOPE_ALL = 1;

    private final DataScopeService dataScopeService;
    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;

    public AdminAuthorizationPolicy(DataScopeService dataScopeService,
                                    AdminUserMapper adminUserMapper,
                                    AdminRoleMapper adminRoleMapper,
                                    AdminUserRoleMapper adminUserRoleMapper,
                                    AdminRoleMenuMapper adminRoleMenuMapper) {
        this.dataScopeService = dataScopeService;
        this.adminUserMapper = adminUserMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminRoleMenuMapper = adminRoleMenuMapper;
    }

    /** 操作者是否超级管理员（上下文缺失按否处理） */
    public boolean isSuperAdminOperator() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        return ctx != null && ctx.isSuperAdmin();
    }

    /** 目标是否持有内置超管角色 */
    public boolean hasBuiltInSuperAdminRole(Long userId) {
        AdminRole superRole = findBuiltInSuperAdminRole();
        if (superRole == null) {
            return false;
        }
        return adminUserRoleMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.admin.entity.AdminUserRole>()
                .eq(com.cloudmart.admin.entity.AdminUserRole::getUserId, userId)
                .eq(com.cloudmart.admin.entity.AdminUserRole::getRoleId, superRole.getId())) > 0;
    }

    /**
     * S03：单条读/写/删的数据范围校验（与列表查询 applyDataScope 同一语义）；
     * 越界按资源不存在处理（隐藏存在性）。
     */
    public void assertWithinDataScope(AdminUser target) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null || ctx.isSuperAdmin()) {
            return;
        }
        DataScopeResult dataScope = dataScopeService.resolveDataScope(ctx.userId());
        // 范围无法解析（管理员无任何角色配置）时按最严格策略：仅本人记录可见
        boolean within = dataScope == null
                ? target.getId().equals(ctx.userId())
                : switch (dataScope.type()) {
            case ALL -> true;
            case CUSTOM, DEPT_AND_CHILD -> target.getDeptId() != null
                    && dataScope.deptIds() != null && dataScope.deptIds().contains(target.getDeptId());
            case DEPT -> target.getDeptId() != null && target.getDeptId().equals(ctx.deptId());
            case SELF -> target.getId().equals(ctx.userId());
        };
        if (!within) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
    }

    /** 删除/禁用类：内置超管目标无条件保护（超级管理员之间也不得经接口互相删除/禁用） */
    public void assertNotBuiltInSuperAdmin(AdminUser target, String action) {
        if (hasBuiltInSuperAdminRole(target.getId())) {
            throw new BusinessException("FORBIDDEN", "内置超级管理员不允许" + action);
        }
    }

    /**
     * S03 集中入口：数据范围 + 超管目标保护——范围外用户/超管目标（非超管操作者）拒绝。
     * 覆盖读、改、删、重置、禁用、分配角色等全部管理动作的对象校验。
     */
    public void assertCanManage(AdminUser target, String action) {
        assertWithinDataScope(target);
        if (!isSuperAdminOperator() && hasBuiltInSuperAdminRole(target.getId())) {
            // 普通管理员触碰内置超管（重置密码/改状态/改角色集均为提权路径）
            throw new BusinessException("FORBIDDEN", "内置超级管理员仅超级管理员可管理");
        }
    }

    /**
     * 禁用时至少保留一名可用内置超管（QA21：最后超级管理员禁用被拒绝）。
     *
     * @param target   目标用户（数据库当前状态）
     * @param newStatus 本次要设置的目标状态（1=启用；非 1 视为禁用）
     */
    public void assertNotLastEnabledSuperAdmin(AdminUser target, Integer newStatus) {
        if (!hasBuiltInSuperAdminRole(target.getId())) {
            return;
        }
        boolean disabling = newStatus == null || newStatus != 1;
        if (!disabling) {
            return;
        }
        long enabledSuperAdmins = countEnabledBuiltInSuperAdmins();
        if (enabledSuperAdmins <= 1) {
            throw new BusinessException("FORBIDDEN", "至少保留一名可用的内置超级管理员");
        }
    }

    /**
     * S03 授权上限：普通管理员只能授予自身权限集（角色→菜单并集）范围内的菜单；
     * 超管不受限。越界整体拒绝（不允许部分静默成功，QA21）。
     */
    public void assertGrantableMenus(Collection<Long> requestedMenuIds) {
        if (requestedMenuIds == null || requestedMenuIds.isEmpty() || isSuperAdminOperator()) {
            return;
        }
        Set<Long> grantable = operatorMenuIds();
        for (Long menuId : requestedMenuIds) {
            if (menuId != null && !grantable.contains(menuId)) {
                throw new BusinessException("FORBIDDEN", "存在超出自身可授予范围的菜单，拒绝授权");
            }
        }
    }

    /** S03 授权上限：普通管理员不能创建/更新为 ALL 数据范围的角色 */
    public void assertGrantableDataScope(Integer requestedDataScope) {
        if (!isSuperAdminOperator() && requestedDataScope != null
                && requestedDataScope == DATA_SCOPE_ALL) {
            throw new BusinessException("FORBIDDEN", "全部数据范围仅超级管理员可授予");
        }
    }

    public AdminRole findBuiltInSuperAdminRole() {
        return adminRoleMapper.selectOne(new LambdaQueryWrapper<AdminRole>()
                .eq(AdminRole::getRoleKey, BUILT_IN_SUPER_ROLE_KEY)
                .last("LIMIT 1"));
    }

    private long countEnabledBuiltInSuperAdmins() {
        AdminRole superRole = findBuiltInSuperAdminRole();
        if (superRole == null) {
            return 0;
        }
        List<com.cloudmart.admin.entity.AdminUserRole> assignments = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.admin.entity.AdminUserRole>()
                        .eq(com.cloudmart.admin.entity.AdminUserRole::getRoleId, superRole.getId()));
        long enabled = 0;
        for (com.cloudmart.admin.entity.AdminUserRole assignment : assignments) {
            AdminUser user = adminUserMapper.selectById(assignment.getUserId());
            if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                enabled++;
            }
        }
        return enabled;
    }

    /** 操作者自身权限集（角色→菜单 ID 并集） */
    private Set<Long> operatorMenuIds() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            return Set.of();
        }
        List<com.cloudmart.admin.entity.AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.admin.entity.AdminUserRole>()
                        .eq(com.cloudmart.admin.entity.AdminUserRole::getUserId, ctx.userId()));
        Set<Long> menuIds = new HashSet<>();
        for (com.cloudmart.admin.entity.AdminUserRole userRole : userRoles) {
            adminRoleMenuMapper.selectList(new LambdaQueryWrapper<com.cloudmart.admin.entity.AdminRoleMenu>()
                            .eq(com.cloudmart.admin.entity.AdminRoleMenu::getRoleId, userRole.getRoleId()))
                    .forEach(roleMenu -> menuIds.add(roleMenu.getMenuId()));
        }
        return menuIds;
    }
}
