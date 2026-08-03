package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.dto.AdminMenuRequest;
import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminMenuServiceImplTest {

    private AdminMenuMapper adminMenuMapper;
    private AdminRoleMenuMapper adminRoleMenuMapper;
    private AdminMenuServiceImpl adminMenuService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(AdminMenu.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminMenuMapper");
            TableInfoHelper.initTableInfo(assistant, AdminMenu.class);
        }
        if (TableInfoHelper.getTableInfo(AdminRoleMenu.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.admin.repository.AdminRoleMenuMapper");
            TableInfoHelper.initTableInfo(assistant, AdminRoleMenu.class);
        }
    }

    @BeforeEach
    void setUp() {
        adminMenuMapper = mock(AdminMenuMapper.class);
        adminRoleMenuMapper = mock(AdminRoleMenuMapper.class);
        adminMenuService = new AdminMenuServiceImpl(adminMenuMapper, adminRoleMenuMapper);
    }

    private AdminMenu buildMenu(Long id, Long parentId, String menuName) {
        AdminMenu menu = new AdminMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(menuName);
        menu.setOrderNum(1);
        menu.setMenuType("M");
        menu.setStatus(1);
        return menu;
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("valid request -> creates menu")
        void create_ValidRequest_ShouldCreate() {
            when(adminMenuMapper.insert(any(AdminMenu.class))).thenReturn(1);

            AdminMenuRequest request = new AdminMenuRequest("Dashboard", 0L, 1, "/dashboard", "dashboard", null, "Dashboard", 0, 0, "M", 1, 1, "dashboard:view", null, null);
            adminMenuService.create(request);

            verify(adminMenuMapper).insert(any(AdminMenu.class));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("menu exists -> updates menu")
        void update_Exists_ShouldUpdate() {
            AdminMenu menu = buildMenu(1L, 0L, "Dashboard");
            when(adminMenuMapper.selectById(1L)).thenReturn(menu);

            AdminMenuRequest request = new AdminMenuRequest("Home", 0L, 1, "/home", "home", null, "Home", 0, 0, "M", 1, 1, "home:view", null, null);
            adminMenuService.update(1L, request);

            assertThat(menu.getMenuName()).isEqualTo("Home");
            verify(adminMenuMapper).updateById(menu);
        }

        @Test
        @DisplayName("menu not found -> throws MENU_NOT_FOUND")
        void update_NotFound_ShouldThrowBusinessException() {
            when(adminMenuMapper.selectById(999L)).thenReturn(null);

            AdminMenuRequest request = new AdminMenuRequest("X", 0L, 1, null, null, null, null, 0, 0, "M", 1, 1, null, null, null);

            assertThatThrownBy(() -> adminMenuService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MENU_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("menu with no children -> deletes menu and role-menu associations")
        void delete_NoChildren_ShouldDelete() {
            AdminMenu menu = buildMenu(1L, 0L, "Dashboard");
            when(adminMenuMapper.selectById(1L)).thenReturn(menu);
            when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            adminMenuService.delete(1L);

            verify(adminMenuMapper).deleteById(anyLong());
            verify(adminRoleMenuMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("menu has children -> throws MENU_HAS_CHILDREN")
        void delete_HasChildren_ShouldThrowBusinessException() {
            AdminMenu menu = buildMenu(1L, 0L, "Dashboard");
            when(adminMenuMapper.selectById(1L)).thenReturn(menu);
            when(adminMenuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> adminMenuService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MENU_HAS_CHILDREN"));
            verify(adminMenuMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("menu not found -> throws MENU_NOT_FOUND")
        void delete_NotFound_ShouldThrowBusinessException() {
            when(adminMenuMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminMenuService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("MENU_NOT_FOUND"));
        }
    }
}
