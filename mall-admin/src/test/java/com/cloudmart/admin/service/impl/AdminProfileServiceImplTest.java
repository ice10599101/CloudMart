package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminProfileResponse;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProfileServiceImplTest {

    private AdminUserMapper adminUserMapper;
    private AdminUserRoleMapper adminUserRoleMapper;
    private AdminRoleMapper adminRoleMapper;
    private AdminRoleMenuMapper adminRoleMenuMapper;
    private AdminMenuMapper adminMenuMapper;
    private PasswordEncoder passwordEncoder;
    private AdminProfileServiceImpl adminProfileService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AdminUser.class, AdminRole.class, AdminMenu.class, AdminUserRole.class, AdminRoleMenu.class}) {
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
        adminRoleMapper = mock(AdminRoleMapper.class);
        adminRoleMenuMapper = mock(AdminRoleMenuMapper.class);
        adminMenuMapper = mock(AdminMenuMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);

        adminProfileService = new AdminProfileServiceImpl(
                adminUserMapper, adminUserRoleMapper, adminRoleMapper,
                adminRoleMenuMapper, adminMenuMapper, passwordEncoder);
    }

    private AdminUser buildUser(Long id, String username) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Test User");
        user.setEmail("test@example.com");
        user.setPhone("13800000000");
        user.setAvatar("avatar.png");
        user.setDeptId(1L);
        user.setStatus(1);
        user.setPassword("encodedPwd");
        user.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return user;
    }

    private AdminRole buildAdminRole(Long id, String roleKey) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setRoleName("Admin Role");
        return role;
    }

    private AdminUserRole buildUserRole(Long id, Long userId, Long roleId) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setId(id);
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("super admin -> returns all permissions wildcard")
        void getProfile_SuperAdmin_ShouldReturnWildcardPermissions() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            AdminUserRole userRole = buildUserRole(1L, 1L, 1L);
            when(adminUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

            AdminRole adminRole = buildAdminRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(adminRole);

            AdminProfileResponse response = adminProfileService.getProfile(1L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("admin");
            assertThat(response.permissions()).containsExactly("*:*:*");
            assertThat(response.roles()).containsExactly("admin");
        }

        @Test
        @DisplayName("normal user -> returns specific permissions from menus")
        void getProfile_NormalUser_ShouldReturnSpecificPermissions() {
            AdminUser user = buildUser(2L, "editor");
            when(adminUserMapper.selectById(2L)).thenReturn(user);

            AdminUserRole userRole = buildUserRole(2L, 2L, 2L);
            when(adminUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

            AdminRole editorRole = buildAdminRole(2L, "editor");
            when(adminRoleMapper.selectById(2L)).thenReturn(editorRole);

            AdminRoleMenu roleMenu = new AdminRoleMenu();
            roleMenu.setId(1L);
            roleMenu.setRoleId(2L);
            roleMenu.setMenuId(10L);
            when(adminRoleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleMenu));

            AdminMenu menu = new AdminMenu();
            menu.setId(10L);
            menu.setPerms("system:user:list");
            when(adminMenuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(menu));

            AdminProfileResponse response = adminProfileService.getProfile(2L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.permissions()).containsExactly("system:user:list");
            assertThat(response.roles()).containsExactly("editor");
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void getProfile_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminProfileService.getProfile(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("user exists -> updates profile fields")
        void updateProfile_Exists_ShouldUpdate() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            adminProfileService.updateProfile(1L, "NewNick", "new@test.com", "13900000000", "new_avatar.png");

            assertThat(user.getNickname()).isEqualTo("NewNick");
            assertThat(user.getEmail()).isEqualTo("new@test.com");
            assertThat(user.getPhone()).isEqualTo("13900000000");
            assertThat(user.getAvatar()).isEqualTo("new_avatar.png");
            verify(adminUserMapper).updateById(user);
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void updateProfile_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminProfileService.updateProfile(999L, "Nick", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));

            verify(adminUserMapper, never()).updateById(any(AdminUser.class));
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePasswordTests {

        @Test
        @DisplayName("correct old password -> updates to new password")
        void updatePassword_CorrectOldPassword_ShouldUpdate() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("oldPwd123", "encodedPwd")).thenReturn(true);
            when(passwordEncoder.encode("newPwd456")).thenReturn("encodedNewPwd");

            adminProfileService.updatePassword(1L, "oldPwd123", "newPwd456");

            assertThat(user.getPassword()).isEqualTo("encodedNewPwd");
            verify(adminUserMapper).updateById(user);
        }

        @Test
        @DisplayName("wrong old password -> throws PASSWORD_MISMATCH")
        void updatePassword_WrongOldPassword_ShouldThrowBusinessException() {
            AdminUser user = buildUser(1L, "admin");
            when(adminUserMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("wrongPwd", "encodedPwd")).thenReturn(false);

            assertThatThrownBy(() -> adminProfileService.updatePassword(1L, "wrongPwd", "newPwd456"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PASSWORD_MISMATCH"));

            verify(adminUserMapper, never()).updateById(any(AdminUser.class));
        }

        @Test
        @DisplayName("user not found -> throws USER_NOT_FOUND")
        void updatePassword_NotFound_ShouldThrowBusinessException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminProfileService.updatePassword(999L, "oldPwd", "newPwd"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USER_NOT_FOUND"));

            verify(adminUserMapper, never()).updateById(any(AdminUser.class));
        }
    }
}
