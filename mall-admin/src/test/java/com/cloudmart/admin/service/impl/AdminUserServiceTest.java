package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminUserRequest;
import com.cloudmart.admin.dto.AdminUserResponse;
import com.cloudmart.admin.dto.AdminUserUpdateRequest;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.entity.AdminUserPost;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminPostMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminUserPostMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.admin.service.DataScopeService;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cloudmart.admin.feign.AuthRevocationFeignClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;
    @Mock
    private AdminUserPostMapper adminUserPostMapper;
    @Mock
    private AdminRoleMapper adminRoleMapper;
    @Mock
    private AdminPostMapper adminPostMapper;
    @Mock
    private AdminDeptMapper adminDeptMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuthRevocationFeignClient authRevocationFeignClient;
    @Mock
    private AdminConverter adminConverter;

    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(
                adminUserMapper, adminUserRoleMapper, adminUserPostMapper,
                adminRoleMapper, adminPostMapper, adminDeptMapper,
                passwordEncoder, dataScopeService, adminConverter,
                authRevocationFeignClient);
    }

    @Test
    void getUserById_found() {
        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("Admin");
        user.setStatus(0);

        AdminUserResponse converterResponse = new AdminUserResponse(
                1L, "admin", "Admin", null, null, null, null,
                null, null, 0, null, null, null, null, null,
                List.of(), List.of()
        );

        when(adminUserMapper.selectById(1L)).thenReturn(user);
        when(adminUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(adminUserPostMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(adminConverter.toUserResponse(user)).thenReturn(converterResponse);

        AdminUserResponse response = adminUserService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.nickname()).isEqualTo("Admin");
    }

    @Test
    void getUserById_notFound_throwsException() {
        when(adminUserMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void createUser_success() {
        AdminUserRequest request = new AdminUserRequest(
                "newuser", "New User", "new@test.com", "13800000000",
                0, null, "password123", 1L,
                List.of(1L, 2L), List.of(1L), 0, null
        );

        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser u = invocation.getArgument(0);
            u.setId(100L);
            return 1;
        });

        adminUserService.create(request);

        ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(userCaptor.capture());
        AdminUser captured = userCaptor.getValue();
        assertThat(captured.getUsername()).isEqualTo("newuser");
        assertThat(captured.getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(captured.getNickname()).isEqualTo("New User");
        assertThat(captured.getEmail()).isEqualTo("new@test.com");
        assertThat(captured.getStatus()).isEqualTo(0);
        verify(adminUserRoleMapper, times(2)).insert(any(AdminUserRole.class));
        verify(adminUserPostMapper).insert(any(AdminUserPost.class));
    }

    @Test
    void createUser_duplicateUsername_throwsException() {
        AdminUserRequest request = new AdminUserRequest(
                "admin", "Admin", "admin@test.com", "13800000001",
                0, null, "password123", 1L, null, null, 0, null
        );

        AdminUser existing = new AdminUser();
        existing.setId(1L);
        existing.setUsername("admin");

        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> adminUserService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USERNAME_EXISTS");
    }

    @Test
    void updateUser_success() {
        Long userId = 1L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("admin");
        user.setNickname("Old Nick");
        user.setEmail("old@test.com");

        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "New Nick", "new@test.com", "13800000000",
                0, null, 1L, List.of(1L), List.of(1L), 0, null
        );

        when(adminUserMapper.selectById(userId)).thenReturn(user);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(adminUserMapper.updateById(user)).thenReturn(1);

        adminUserService.update(userId, request);

        assertThat(user.getNickname()).isEqualTo("New Nick");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getPhone()).isEqualTo("13800000000");
        verify(adminUserMapper).updateById(user);
        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(adminUserPostMapper).delete(any(LambdaQueryWrapper.class));
        verify(adminUserRoleMapper).insert(any(AdminUserRole.class));
        verify(adminUserPostMapper).insert(any(AdminUserPost.class));
    }

    @Test
    void deleteUser_success() {
        Long userId = 1L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("admin");

        when(adminUserMapper.selectById(userId)).thenReturn(user);
        when(adminUserMapper.deleteById(userId)).thenReturn(1);

        adminUserService.delete(userId);

        verify(adminUserMapper).deleteById(userId);
        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(adminUserPostMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void assignRoles_success() {
        Long userId = 1L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("admin");

        List<Long> roleIds = List.of(10L, 20L, 30L);

        when(adminUserMapper.selectById(userId)).thenReturn(user);

        adminUserService.assignRoles(userId, roleIds);

        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<AdminUserRole> roleCaptor = ArgumentCaptor.forClass(AdminUserRole.class);
        verify(adminUserRoleMapper, times(3)).insert(roleCaptor.capture());
        List<AdminUserRole> capturedRoles = roleCaptor.getAllValues();
        assertThat(capturedRoles).allMatch(r ->
                roleIds.contains(r.getRoleId()) && userId.equals(r.getUserId())
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-03：禁用用户走硬失效（版本递增+撤销全部刷新家族）")
    void update_disabling_hardInvalidates() {
        Long userId = 1L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("user1");
        user.setStatus(0);

        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "user1", null, null, 0, null, 1L, List.of(), List.of(), 1, null);

        when(adminUserMapper.selectById(userId)).thenReturn(user);
        when(adminUserMapper.updateById(user)).thenReturn(1);

        adminUserService.update(userId, request);

        ArgumentCaptor<java.util.Map<String, Object>> captor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(authRevocationFeignClient).invalidateState(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .containsEntry("subjectType", "ADMIN")
                .containsEntry("revokeRefreshTokens", true);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-03：内置超管不允许删除")
    void delete_builtInSuperAdmin_rejected() {
        Long userId = 1L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("admin");

        com.cloudmart.admin.entity.AdminRole superRole = new com.cloudmart.admin.entity.AdminRole();
        superRole.setId(9L);
        superRole.setRoleKey("admin");
        AdminUserRole superUserRole = new AdminUserRole();
        superUserRole.setUserId(userId);
        superUserRole.setRoleId(9L);

        when(adminUserMapper.selectById(userId)).thenReturn(user);
        when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(superRole);
        when(adminUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(superUserRole));

        assertThatThrownBy(() -> adminUserService.delete(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verify(adminUserMapper, times(0)).deleteById(userId);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-03：非超管操作者不能授予内置超管角色")
    void assignRoles_grantSuperRole_byNonSuperOperator_forbidden() {
        Long userId = 2L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("ops");

        com.cloudmart.admin.entity.AdminRole superRole = new com.cloudmart.admin.entity.AdminRole();
        superRole.setId(9L);
        superRole.setRoleKey("admin");

        com.cloudmart.common.context.AdminSecurityContext.set(
                new com.cloudmart.common.context.AdminSecurityContext(
                        100L, "operator", "admin", java.util.Set.of("system:user:list"), null));

        try {
            when(adminUserMapper.selectById(userId)).thenReturn(user);
            when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(superRole);
            when(dataScopeService.resolveDataScope(100L)).thenReturn(
                    new com.cloudmart.common.datascope.DataScopeResult(
                            com.cloudmart.common.datascope.DataScopeType.ALL, null));

            assertThatThrownBy(() -> adminUserService.assignRoles(userId, List.of(9L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        } finally {
            com.cloudmart.common.context.AdminSecurityContext.clear();
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-03：assignRoles 软失效目标（仅版本递增）")
    void assignRoles_softInvalidatesTarget() {
        Long userId = 3L;
        AdminUser user = new AdminUser();
        user.setId(userId);
        user.setUsername("viewer");

        when(adminUserMapper.selectById(userId)).thenReturn(user);
        when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        adminUserService.assignRoles(userId, List.of(10L));

        ArgumentCaptor<java.util.Map<String, Object>> captor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(authRevocationFeignClient).invalidateState(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .containsEntry("revokeRefreshTokens", false);
    }
}
