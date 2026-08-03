package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.*;
import com.cloudmart.admin.entity.*;
import com.cloudmart.admin.repository.*;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.admin.service.DataScopeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminUserServiceImplTest {

    private AdminUserMapper adminUserMapper;
    private AdminUserRoleMapper adminUserRoleMapper;
    private AdminUserPostMapper adminUserPostMapper;
    private AdminRoleMapper adminRoleMapper;
    private AdminPostMapper adminPostMapper;
    private AdminDeptMapper adminDeptMapper;
    private PasswordEncoder passwordEncoder;
    private DataScopeService dataScopeService;
    private AdminConverter adminConverter;
    private AdminUserServiceImpl adminUserService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AdminUser.class, AdminRole.class, AdminMenu.class, AdminDept.class, AdminPost.class, AdminUserRole.class, AdminUserPost.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.admin.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        adminUserMapper = mock(AdminUserMapper.class);
        adminUserRoleMapper = mock(AdminUserRoleMapper.class);
        adminUserPostMapper = mock(AdminUserPostMapper.class);
        adminRoleMapper = mock(AdminRoleMapper.class);
        adminPostMapper = mock(AdminPostMapper.class);
        adminDeptMapper = mock(AdminDeptMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        dataScopeService = mock(DataScopeService.class);
        adminConverter = mock(AdminConverter.class);

        adminUserService = new AdminUserServiceImpl(
                adminUserMapper, adminUserRoleMapper, adminUserPostMapper,
                adminRoleMapper, adminPostMapper, adminDeptMapper,
                passwordEncoder, dataScopeService, adminConverter);
    }

    private AdminUser buildUser(Long id, String username) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Test User");
        user.setPassword("encodedPwd");
        user.setStatus(1);
        user.setDeptId(1L);
        return user;
    }

    private AdminUserResponse buildUserResponse(AdminUser user) {
        return new AdminUserResponse(
                user.getId(), user.getUsername(), user.getNickname(),
                "test@test.com", "13800000000", 1, null,
                user.getDeptId(), "Test Dept", user.getStatus(), null,
                null, null, null, null,
                List.of(), List.of()
        );
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("user exists -> returns response")
        void getById_Exists_ShouldReturnResponse() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);
            when(adminConverter.toUserResponse(user)).thenReturn(buildUserResponse(user));
            when(adminUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(adminUserPostMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            var response = adminUserService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.username()).isEqualTo("admin");
            verify(adminConverter).toUserResponse(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("unique username -> creates user with roles and posts")
        void create_UniqueUsername_ShouldCreate() {
            when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPwd");
            when(adminUserMapper.insert(any(AdminUser.class))).thenReturn(1);

            AdminUserRequest request = new AdminUserRequest("newuser", "Nick", "e@t.com", "138", 1, null, "password123", 1L, List.of(1L), List.of(1L), 1, null);
            adminUserService.create(request);

            verify(adminUserMapper).insert(any(AdminUser.class));
            verify(adminUserRoleMapper).insert(any(AdminUserRole.class));
            verify(adminUserPostMapper).insert(any(AdminUserPost.class));
        }

        @Test
        @DisplayName("duplicate username -> throws USERNAME_EXISTS")
        void create_DuplicateUsername_ShouldThrowBusinessException() {
            AdminUser existing = buildUser(2L, "admin");
            when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            AdminUserRequest request = new AdminUserRequest("admin", "Nick", "e@t.com", "138", 1, null, "password123", 1L, null, null, 1, null);

            assertThatThrownBy(() -> adminUserService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USERNAME_EXISTS"));
            verify(adminUserMapper, never()).insert(any(AdminUser.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("user exists and unique username -> updates user")
        void update_ExistsAndUnique_ShouldUpdate() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);
            when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AdminUserUpdateRequest request = new AdminUserUpdateRequest("NewNick", "e@t.com", "138", 1, null, 1L, List.of(1L), List.of(1L), 1, null);
            adminUserService.update(1L, request);

            verify(adminUserMapper).updateById(user);
            verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminUserPostMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            AdminUserUpdateRequest request = new AdminUserUpdateRequest("Nick", null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> adminUserService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("user exists -> deletes user and associations")
        void delete_Exists_ShouldDelete() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            adminUserService.delete(1L);

            verify(adminUserMapper).deleteById(anyLong());
            verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminUserPostMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTests {

        @Test
        @DisplayName("user exists -> resets password")
        void resetPassword_UserExists_ShouldReset() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.encode("newPwd123")).thenReturn("encodedNewPwd");

            AdminResetPwdRequest request = new AdminResetPwdRequest(1L, "newPwd123");
            adminUserService.resetPassword(request);

            assertThat(user.getPassword()).isEqualTo("encodedNewPwd");
            verify(adminUserMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void resetPassword_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            AdminResetPwdRequest request = new AdminResetPwdRequest(999L, "newPwd123");

            assertThatThrownBy(() -> adminUserService.resetPassword(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("user exists -> updates status")
        void updateStatus_Exists_ShouldUpdate() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            adminUserService.updateStatus(1L, 0);

            assertThat(user.getStatus()).isEqualTo(0);
            verify(adminUserMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void updateStatus_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updateStatus(999L, 0))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("assignRoles")
    class AssignRolesTests {

        @Test
        @DisplayName("user exists -> clears old and assigns new roles")
        void assignRoles_UserExists_ShouldAssign() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            adminUserService.assignRoles(1L, List.of(1L));

            verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminUserRoleMapper).insert(any(AdminUserRole.class));
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void assignRoles_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.assignRoles(999L, List.of(1L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }
}
