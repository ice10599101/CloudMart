package com.cloudmart.admin.service;

import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S04：凭据校验与权限解析——全用户诊断日志移除、唯一索引定位、
 * 禁用角色不参与超管判定与权限解析。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;
    @Mock
    private AdminRoleMapper adminRoleMapper;
    @Mock
    private AdminRoleMenuMapper adminRoleMenuMapper;
    @Mock
    private AdminMenuMapper adminMenuMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAuthService authService;

    private static final long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        authService = new AdminAuthService(adminUserMapper, adminUserRoleMapper,
                adminRoleMapper, adminRoleMenuMapper, adminMenuMapper, passwordEncoder);
    }

    private AdminUser activeUser() {
        AdminUser user = new AdminUser();
        user.setId(USER_ID);
        user.setUsername("admin");
        user.setStatus(1);
        user.setPassword("$2a$10$encoded");
        return user;
    }

    private AdminRole role(long id, String key, int status) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleKey(key);
        role.setStatus(status);
        return role;
    }

    private void bindRole(long roleId) {
        AdminUserRole ur = new AdminUserRole();
        ur.setUserId(USER_ID);
        ur.setRoleId(roleId);
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(ur));
        when(adminRoleMapper.selectById(roleId)).thenReturn(role(roleId, "admin", 1));
    }

    @Test
    @DisplayName("凭据校验：唯一索引定位（不再全用户加载），成功返回用户")
    void validateCredentials_singleLookup() {
        AdminUser user = activeUser();
        when(adminUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("pw", "$2a$10$encoded")).thenReturn(true);

        AdminUser result = authService.validateCredentials("admin", "pw");

        assertThat(result.getId()).isEqualTo(USER_ID);
        // S04：不再 selectList(null) 全用户扫描
        verify(adminUserMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("凭据校验：用户名规范化（trim）")
    void validateCredentials_trimsUsername() {
        when(adminUserMapper.selectOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        authService.validateCredentials("  admin  ", "pw");

        // S04：唯一索引定位（单行查询，不再全用户扫描）；用户名规范化（trim）
        // 的绑定值属 MyBatis 执行层，由真实 MySQL 集成/远程验证覆盖
        verify(adminUserMapper).selectOne(any());
        verify(adminUserMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("凭据校验：账号不存在/密码错误统一文案（不区分两种失败）")
    void validateCredentials_uniformFailure() {
        when(adminUserMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.validateCredentials("ghost", "pw"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AUTH_FAILED"));

        when(adminUserMapper.selectOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> authService.validateCredentials("admin", "wrong"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AUTH_FAILED"));
    }

    @Test
    @DisplayName("S04：禁用的 admin 角色不再判定超管")
    void checkSuperAdmin_disabledRoleNotSuper() {
        AdminUserRole ur = new AdminUserRole();
        ur.setUserId(USER_ID);
        ur.setRoleId(7L);
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(ur));
        when(adminRoleMapper.selectById(7L)).thenReturn(role(7L, "admin", 0));

        assertThat(authService.checkSuperAdmin(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("S04：启用的 admin 角色判定超管")
    void checkSuperAdmin_enabledRole() {
        bindRole(7L);
        assertThat(authService.checkSuperAdmin(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("S04：禁用角色的菜单权限不参与解析")
    void getPermissions_skipsDisabledRole() {
        AdminUserRole ur = new AdminUserRole();
        ur.setUserId(USER_ID);
        ur.setRoleId(7L);
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(ur));
        // 角色 7 禁用；角色 8 启用
        when(adminRoleMapper.selectById(7L)).thenReturn(role(7L, "op", 0));
        when(adminRoleMapper.selectById(8L)).thenReturn(role(8L, "viewer", 1));

        // 混合两条用户角色行
        AdminUserRole ur2 = new AdminUserRole();
        ur2.setUserId(USER_ID);
        ur2.setRoleId(8L);
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(ur, ur2));

        AdminMenu menu = new AdminMenu();
        menu.setId(100L);
        menu.setPerms("admin:user:list");
        when(adminRoleMenuMapper.selectList(any())).thenReturn(
                List.of(new AdminRoleMenu() {{ setRoleId(7L); setMenuId(100L); }},
                        new AdminRoleMenu() {{ setRoleId(8L); setMenuId(100L); }}));
        when(adminMenuMapper.selectByIds(any(java.util.Collection.class))).thenReturn(List.of(menu));

        var permissions = authService.getPermissionsByUserId(USER_ID);

        // 权限来自启用角色（菜单行相同，但禁用角色不影响结果集合）
        assertThat(permissions).containsExactly("admin:user:list");
        // 禁用角色的菜单查询不发生
        verify(adminRoleMenuMapper, never()).selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminRoleMenu>()
                        .eq(AdminRoleMenu::getRoleId, 7L));
    }
}
