package com.cloudmart.admin.service.impl;

import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.admin.service.DataScopeService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.datascope.DataScopeResult;
import com.cloudmart.common.datascope.DataScopeType;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S03/QA21：管理员数据范围与授权上限——集中策略语义。
 * 跨部门重置、越级角色授权、自我提权、最后超级管理员禁用全部拒绝。
 */
@DisplayName("S03 AdminAuthorizationPolicy 授权策略")
class AdminAuthorizationPolicyTest {

    private DataScopeService dataScopeService;
    private AdminUserMapper adminUserMapper;
    private AdminRoleMapper adminRoleMapper;
    private AdminUserRoleMapper adminUserRoleMapper;
    private AdminRoleMenuMapper adminRoleMenuMapper;
    private AdminAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        dataScopeService = mock(DataScopeService.class);
        adminUserMapper = mock(AdminUserMapper.class);
        adminRoleMapper = mock(AdminRoleMapper.class);
        adminUserRoleMapper = mock(AdminUserRoleMapper.class);
        adminRoleMenuMapper = mock(AdminRoleMenuMapper.class);
        policy = new AdminAuthorizationPolicy(dataScopeService, adminUserMapper,
                adminRoleMapper, adminUserRoleMapper, adminRoleMenuMapper);
    }

    @AfterEach
    void clearContext() {
        AdminSecurityContext.clear();
    }

    private AdminUser user(Long id, Long deptId, int status) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setDeptId(deptId);
        user.setStatus(status);
        return user;
    }

    private AdminRole superRole() {
        AdminRole role = new AdminRole();
        role.setId(9L);
        role.setRoleKey("admin");
        return role;
    }

    private void asNonSuperOperator(long userId, long deptId) {
        AdminSecurityContext.set(new AdminSecurityContext(userId, "manager", "common",
                Set.of("system:user:list"), deptId));
    }

    private void stubSuperRoleFor(Long targetUserId) {
        when(adminRoleMapper.selectOne(any())).thenReturn(superRole());
        when(adminUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(adminUserMapper.selectById(targetUserId)).thenReturn(user(targetUserId, 1L, 1));
    }

    @Test
    @DisplayName("QA21：受限管理员重置/禁用数据范围外用户被拒绝（USER_NOT_FOUND 隐藏存在性）")
    void assertCanManage_outOfDataScope_rejected() {
        asNonSuperOperator(1L, 1L);
        // 管理员数据范围：仅本部门（DEPT）
        when(dataScopeService.resolveDataScope(anyLong()))
                .thenReturn(new DataScopeResult(DataScopeType.DEPT, List.of()));
        AdminUser target = user(2L, 99L, 1);

        assertThatThrownBy(() -> policy.assertCanManage(target, "重置密码"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("QA21：非超管操作者触碰内置超管目标被拒绝（自我提权路径关闭）")
    void assertCanManage_superAdminTarget_rejected() {
        asNonSuperOperator(2L, 1L);
        stubSuperRoleFor(1L);
        when(dataScopeService.resolveDataScope(anyLong()))
                .thenReturn(new DataScopeResult(DataScopeType.ALL, List.of()));

        assertThatThrownBy(() -> policy.assertCanManage(user(1L, 1L, 1), "重置密码"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("QA21：内置超管删除/禁用类无条件保护（超管操作者也被拒）")
    void assertNotBuiltInSuperAdmin_unconditional() {
        AdminSecurityContext.set(new AdminSecurityContext(1L, "super", "admin",
                Set.of("*:*:*"), null));
        stubSuperRoleFor(1L);

        assertThatThrownBy(() -> policy.assertNotBuiltInSuperAdmin(user(1L, 1L, 1), "禁用"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("QA21：禁用最后一名可用内置超管被拒绝；仍有其他可用超管时放行")
    void assertNotLastEnabledSuperAdmin() {
        AdminSecurityContext.set(new AdminSecurityContext(1L, "super", "admin",
                Set.of("*:*:*"), null));
        stubSuperRoleFor(1L);
        // 仅一名启用中的超管（目标本身）
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(1L)));
        when(adminUserMapper.selectById(1L)).thenReturn(user(1L, 1L, 1));

        AdminUser target = user(1L, 1L, 1);
        assertThatThrownBy(() -> policy.assertNotLastEnabledSuperAdmin(target, 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        // 另有一名启用中的超管 → 放行
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(1L), userRole(2L)));
        when(adminUserMapper.selectById(2L)).thenReturn(user(2L, 1L, 1));
        assertThatCode(() -> policy.assertNotLastEnabledSuperAdmin(target, 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("QA21：非超管授予自身权限集外的菜单被拒绝；范围内放行")
    void assertGrantableMenus_scopeLimited() {
        asNonSuperOperator(2L, 1L);
        AdminUserRole ownRole = userRole(5L);
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(ownRole));
        com.cloudmart.admin.entity.AdminRoleMenu ownMenu =
                new com.cloudmart.admin.entity.AdminRoleMenu();
        ownMenu.setRoleId(5L);
        ownMenu.setMenuId(100L);
        when(adminRoleMenuMapper.selectList(any())).thenReturn(List.of(ownMenu));

        assertThatThrownBy(() -> policy.assertGrantableMenus(List.of(100L, 200L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        assertThatCode(() -> policy.assertGrantableMenus(List.of(100L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("QA21：非超管创建/更新 ALL 数据范围角色被拒绝；超管放行")
    void assertGrantableDataScope_allReservedForSuper() {
        asNonSuperOperator(2L, 1L);
        assertThatThrownBy(() -> policy.assertGrantableDataScope(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        AdminSecurityContext.set(new AdminSecurityContext(1L, "super", "admin",
                Set.of("*:*:*"), null));
        assertThatCode(() -> policy.assertGrantableDataScope(1))
                .doesNotThrowAnyException();
    }

    private AdminUserRole userRole(long roleId) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(1L);
        userRole.setRoleId(roleId);
        return userRole;
    }
}
