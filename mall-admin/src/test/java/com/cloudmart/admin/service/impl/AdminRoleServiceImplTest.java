package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminRoleDataScopeRequest;
import com.cloudmart.admin.dto.AdminRoleMenuRequest;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleDept;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.repository.AdminRoleDeptMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminRoleServiceImplTest {

    private AdminRoleMapper adminRoleMapper;
    private AdminRoleMenuMapper adminRoleMenuMapper;
    private AdminRoleDeptMapper adminRoleDeptMapper;
    private AdminUserRoleMapper adminUserRoleMapper;
    private AdminConverter adminConverter;
    private AdminRoleServiceImpl adminRoleService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminRole.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminRoleMapper");
            TableInfoHelper.initTableInfo(assistant, AdminRole.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminRoleMapper = mock(AdminRoleMapper.class);
        adminRoleMenuMapper = mock(AdminRoleMenuMapper.class);
        adminRoleDeptMapper = mock(AdminRoleDeptMapper.class);
        adminUserRoleMapper = mock(AdminUserRoleMapper.class);
        adminConverter = mock(AdminConverter.class);
        adminRoleService = new AdminRoleServiceImpl(adminRoleMapper, adminRoleMenuMapper, adminRoleDeptMapper, adminUserRoleMapper, adminConverter);
    }

    private AdminRole buildRole(Long id, String roleKey) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleName("Test Role");
        role.setRoleKey(roleKey);
        role.setRoleSort(1);
        role.setDataScope(1);
        role.setStatus(1);
        return role;
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("role exists -> returns response")
        void getById_Exists_ShouldReturnResponse() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);
            when(adminConverter.toRoleResponse(role)).thenReturn(new AdminRoleResponse(1L, "Test Role", "admin", 1, 1, null, null, 1, null, null));

            var response = adminRoleService.getById(1L);

            assertThat(response).isNotNull();
            verify(adminConverter).toRoleResponse(role);
        }

        @Test
        @DisplayName("role not found -> throws ROLE_NOT_FOUND")
        void getById_NotFound_ShouldThrowBusinessException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminRoleService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("unique roleKey -> creates role with menus")
        void create_UniqueRoleKey_ShouldCreate() {
            when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminRoleMapper.insert(any(AdminRole.class))).thenReturn(1);

            AdminRoleRequest request = new AdminRoleRequest("Editor", "editor", 2, 1, null, null, 1, null, List.of(1L));
            adminRoleService.create(request);

            verify(adminRoleMapper).insert(any(AdminRole.class));
            verify(adminRoleMenuMapper).insert(any(AdminRoleMenu.class));
        }

        @Test
        @DisplayName("duplicate roleKey -> throws ROLE_KEY_EXISTS")
        void create_DuplicateRoleKey_ShouldThrowBusinessException() {
            AdminRole existing = buildRole(2L, "admin");
            when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            AdminRoleRequest request = new AdminRoleRequest("Admin", "admin", 1, 1, null, null, 1, null, null);

            assertThatThrownBy(() -> adminRoleService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_KEY_EXISTS"));
            verify(adminRoleMapper, never()).insert(any(AdminRole.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("role exists and unique key -> updates role")
        void update_ExistsAndUnique_ShouldUpdate() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);
            when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AdminRoleRequest request = new AdminRoleRequest("Super Admin", "admin", 1, 1, null, null, 1, null, List.of(1L));
            adminRoleService.update(1L, request);

            verify(adminRoleMapper).updateById(role);
            verify(adminRoleMenuMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminRoleDeptMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("role not found -> throws ROLE_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            AdminRoleRequest request = new AdminRoleRequest("X", "x", 1, 1, null, null, 1, null, null);

            assertThatThrownBy(() -> adminRoleService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("role not in use -> deletes role and associations")
        void delete_NotInUse_ShouldDelete() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);
            when(adminUserRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            adminRoleService.delete(1L);

            verify(adminRoleMapper).deleteById(anyLong());
            verify(adminRoleMenuMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminRoleDeptMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("role in use -> throws ROLE_IN_USE")
        void delete_InUse_ShouldThrowBusinessException() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);
            when(adminUserRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> adminRoleService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_IN_USE"));
            verify(adminRoleMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("role not found -> throws ROLE_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminRoleService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("assignMenus")
    class AssignMenusTests {

        @Test
        @DisplayName("role exists -> clears old and assigns new menus")
        void assignMenus_RoleExists_ShouldAssign() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);

            AdminRoleMenuRequest request = new AdminRoleMenuRequest(1L, List.of(1L));
            adminRoleService.assignMenus(request);

            verify(adminRoleMenuMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminRoleMenuMapper).insert(any(AdminRoleMenu.class));
        }

        @Test
        @DisplayName("role exists with multiple menus -> inserts each menu")
        void assignRoles_MultipleMenus_ShouldInsertEach() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);

            AdminRoleMenuRequest request = new AdminRoleMenuRequest(1L, List.of(1L, 2L, 3L));
            adminRoleService.assignMenus(request);

            verify(adminRoleMenuMapper, atLeast(3)).insert(any(AdminRoleMenu.class));
        }

        @Test
        @DisplayName("role not found -> throws ROLE_NOT_FOUND")
        void assignMenus_NotFound_ShouldThrowBusinessException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            AdminRoleMenuRequest request = new AdminRoleMenuRequest(999L, List.of(1L));

            assertThatThrownBy(() -> adminRoleService.assignMenus(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateDataScope")
    class UpdateDataScopeTests {

        @Test
        @DisplayName("role exists with CUSTOM scope and deptIds -> saves dept associations")
        void updateDataScope_CustomWithDepts_ShouldSaveDepts() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);

            AdminRoleDataScopeRequest request = new AdminRoleDataScopeRequest(2, List.of(1L));
            adminRoleService.updateDataScope(1L, request);

            verify(adminRoleDeptMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminRoleDeptMapper).insert(any(AdminRoleDept.class));
            verify(adminRoleMapper).updateById(role);
        }

        @Test
        @DisplayName("role exists with ALL scope -> clears dept associations")
        void updateDataScope_AllScope_ShouldClearDepts() {
            AdminRole role = buildRole(1L, "admin");
            when(adminRoleMapper.selectById(1L)).thenReturn(role);

            AdminRoleDataScopeRequest request = new AdminRoleDataScopeRequest(1, null);
            adminRoleService.updateDataScope(1L, request);

            verify(adminRoleDeptMapper).delete(any(LambdaQueryWrapper.class));
            verify(adminRoleDeptMapper, never()).insert(any(AdminRoleDept.class));
        }

        @Test
        @DisplayName("role not found -> throws ROLE_NOT_FOUND")
        void updateDataScope_NotFound_ShouldThrowBusinessException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            AdminRoleDataScopeRequest request = new AdminRoleDataScopeRequest(1, null);

            assertThatThrownBy(() -> adminRoleService.updateDataScope(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_FOUND"));
        }
    }
}
