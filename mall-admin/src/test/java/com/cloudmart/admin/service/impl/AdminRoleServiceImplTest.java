package com.cloudmart.admin.service.impl;

import com.cloudmart.admin.feign.AuthRevocationFeignClient;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminRoleMenuRequest;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADMIN-01：组织模型退役后的角色服务回归（dataScope/部门分配链路已删除）。
 */
@SuppressWarnings("unchecked")
class AdminRoleServiceImplTest {

    private AdminRoleMapper adminRoleMapper;
    private AdminRoleMenuMapper adminRoleMenuMapper;
    private AdminUserRoleMapper adminUserRoleMapper;
    private AdminConverter adminConverter;
    private AuthRevocationFeignClient authRevocationFeignClient;
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
        adminUserRoleMapper = mock(AdminUserRoleMapper.class);
        adminConverter = mock(AdminConverter.class);
        authRevocationFeignClient = mock(AuthRevocationFeignClient.class);
        com.cloudmart.admin.service.impl.AdminAuthorizationPolicy authorizationPolicy =
                new com.cloudmart.admin.service.impl.AdminAuthorizationPolicy(
                        mock(com.cloudmart.admin.service.DataScopeService.class),
                        mock(com.cloudmart.admin.repository.AdminUserMapper.class),
                        adminRoleMapper, adminUserRoleMapper, adminRoleMenuMapper);
        adminRoleService = new AdminRoleServiceImpl(adminRoleMapper, adminRoleMenuMapper,
                adminUserRoleMapper, adminConverter, authRevocationFeignClient, authorizationPolicy);
        com.cloudmart.common.context.AdminSecurityContext.set(new com.cloudmart.common.context.AdminSecurityContext(
                1L, "super", "admin", java.util.Set.of("*:*:*"), null));
        org.mockito.Mockito.lenient().when(adminRoleMapper.updateById(org.mockito.ArgumentMatchers.any(com.cloudmart.admin.entity.AdminRole.class)))
                .thenReturn(1);
    }

    @AfterEach
    void clearSecurityContext() {
        com.cloudmart.common.context.AdminSecurityContext.clear();
    }

    private AdminRole buildRole(Long id, String roleKey) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleName("Test Role");
        role.setRoleKey(roleKey);
        role.setRoleSort(1);
        role.setStatus(1);
        return role;
    }

    @Test
    @DisplayName("create：dataScope 字段已退役——请求不再含 dataScope，写入不再落部门")
    void create_withoutDataScope() {
        AdminRoleRequest request = new AdminRoleRequest("编辑", "editor", 2,
                1, 1, 0, "运营角色", java.util.List.of());
        when(adminRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        adminRoleService.create(request);

        verify(adminRoleMapper).insert(any(AdminRole.class));
    }

    @Test
    @DisplayName("getById：角色存在返回（无部门数据范围字段）")
    void getById_found() {
        when(adminRoleMapper.selectById(1L)).thenReturn(buildRole(1L, "editor"));
        when(adminRoleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of());
        when(adminConverter.toRoleResponse(any(com.cloudmart.admin.entity.AdminRole.class)))
                .thenReturn(new AdminRoleResponse(1L, "Test Role", "editor", 1, 1, 1, 1, null, java.time.LocalDateTime.now()));

        var response = adminRoleService.getById(1L);
        assertThat(response).isNotNull();
        assertThat(response.roleKey()).isEqualTo("editor");
    }

    @Test
    @DisplayName("getById：角色不存在抛 ROLE_NOT_FOUND")
    void getById_notFound() {
        when(adminRoleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminRoleService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("delete：内置超管角色保护（不允许删除）")
    void delete_builtinProtected() {
        // 非超管操作者触碰内置超管角色 → FORBIDDEN
        com.cloudmart.common.context.AdminSecurityContext.set(new com.cloudmart.common.context.AdminSecurityContext(
                9L, "ops", "common", java.util.Set.of("admin:role:remove"), null));
        when(adminRoleMapper.selectById(1L)).thenReturn(buildRole(1L, "admin"));

        assertThatThrownBy(() -> adminRoleService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        verify(adminRoleMapper, never()).deleteById(1L);
    }
}
