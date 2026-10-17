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

    private AdminUser user(Long id, int status) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private AdminRole superRole() {
        AdminRole role = new AdminRole();
        role.setId(9L);
        role.setRoleKey("admin");
        return role;
    }

    private void asNonSuperOperator(long userId) {
        AdminSecurityContext.set(new AdminSecurityContext(userId, "manager", "common",
                Set.of("system:user:list"), null));
    }

    private void stubSuperRoleFor(Long targetUserId) {
        when(adminRoleMapper.selectOne(any())).thenReturn(superRole());
        when(adminUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(adminUserMapper.selectById(targetUserId)).thenReturn(user(targetUserId, 1));
    }


    private AdminUserRole userRole(long roleId) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(1L);
        userRole.setRoleId(roleId);
        return userRole;
    }
}
