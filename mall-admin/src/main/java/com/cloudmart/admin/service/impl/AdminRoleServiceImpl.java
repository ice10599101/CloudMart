package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminRoleDataScopeRequest;
import com.cloudmart.admin.dto.AdminRoleDeptRequest;
import com.cloudmart.admin.dto.AdminRoleMenuRequest;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleDept;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminRoleDeptMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.admin.service.AdminRoleService;
import com.cloudmart.admin.feign.AuthRevocationFeignClient;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminRoleDeptMapper adminRoleDeptMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminConverter adminConverter;
    private final AuthRevocationFeignClient authRevocationFeignClient;

    public AdminRoleServiceImpl(AdminRoleMapper adminRoleMapper,
                                AdminRoleMenuMapper adminRoleMenuMapper,
                                AdminRoleDeptMapper adminRoleDeptMapper,
                                AdminUserRoleMapper adminUserRoleMapper,
                                AdminConverter adminConverter,
                                AuthRevocationFeignClient authRevocationFeignClient) {
        this.adminRoleMapper = adminRoleMapper;
        this.adminRoleMenuMapper = adminRoleMenuMapper;
        this.adminRoleDeptMapper = adminRoleDeptMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminConverter = adminConverter;
        this.authRevocationFeignClient = authRevocationFeignClient;
    }

    @Override
    public List<AdminRoleResponse> list() {
        return adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>().orderByAsc(AdminRole::getRoleSort)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public AdminRoleResponse getById(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        return toResponse(role);
    }

    @Override
    @Transactional
    public void create(AdminRoleRequest request) {
        checkRoleKeyUnique(request.roleKey(), null);

        AdminRole role = new AdminRole();
        role.setRoleName(request.roleName());
        role.setRoleKey(request.roleKey());
        role.setRoleSort(request.roleSort());
        role.setDataScope(request.dataScope());
        role.setMenuCheckStrictly(request.menuCheckStrictly());
        role.setDeptCheckStrictly(request.deptCheckStrictly());
        role.setStatus(request.status() != null ? request.status() : 0);
        role.setRemark(request.remark());
        adminRoleMapper.insert(role);

        saveRoleMenus(role.getId(), request.menuIds());
    }

    @Override
    @Transactional
    public void update(Long id, AdminRoleRequest request) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        checkRoleKeyUnique(request.roleKey(), id);

        role.setRoleName(request.roleName());
        role.setRoleKey(request.roleKey());
        role.setRoleSort(request.roleSort());
        role.setDataScope(request.dataScope());
        role.setMenuCheckStrictly(request.menuCheckStrictly());
        role.setDeptCheckStrictly(request.deptCheckStrictly());
        role.setStatus(request.status());
        role.setRemark(request.remark());
        adminRoleMapper.updateById(role);

        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, id));
        adminRoleDeptMapper.delete(new LambdaQueryWrapper<AdminRoleDept>().eq(AdminRoleDept::getRoleId, id));
        saveRoleMenus(id, request.menuIds());

        // SEC-03：权限集变更软失效（递增认证状态版本）——存量访问令牌立即失效，
        // 持有用户刷新后自动携带新权限集；失败向上抛出保证管理员可感知
        invalidateRoleHolders(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        Long userCount = adminUserRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, id)
        );
        if (userCount > 0) {
            throw new BusinessException("ROLE_IN_USE", "该角色已分配给用户，无法删除");
        }

        adminRoleMapper.deleteById(id);
        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, id));
        adminRoleDeptMapper.delete(new LambdaQueryWrapper<AdminRoleDept>().eq(AdminRoleDept::getRoleId, id));
    }

    @Override
    @Transactional
    public void assignMenus(AdminRoleMenuRequest request) {
        AdminRole role = adminRoleMapper.selectById(request.roleId());
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, request.roleId()));
        saveRoleMenus(request.roleId(), request.menuIds());

        // SEC-03：菜单授权变更软失效，同 update
        invalidateRoleHolders(request.roleId());
    }

    @Override
    @Transactional
    public void assignDepts(AdminRoleDeptRequest request) {
        AdminRole role = adminRoleMapper.selectById(request.roleId());
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        adminRoleDeptMapper.delete(new LambdaQueryWrapper<AdminRoleDept>().eq(AdminRoleDept::getRoleId, request.roleId()));
        saveRoleDepts(request.roleId(), request.deptIds());

        // SEC-03：部门授权范围变更按策略软失效，持有者刷新后按新范围生效
        invalidateRoleHolders(request.roleId());
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        AdminRole role = adminRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        List<AdminRoleMenu> roleMenus = adminRoleMenuMapper.selectList(
                new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, roleId)
        );
        return roleMenus.stream().map(AdminRoleMenu::getMenuId).toList();
    }

    @Override
    @Transactional
    public void updateDataScope(Long roleId, AdminRoleDataScopeRequest request) {
        AdminRole role = adminRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        role.setDataScope(request.dataScope());
        adminRoleMapper.updateById(role);

        adminRoleDeptMapper.delete(new LambdaQueryWrapper<AdminRoleDept>().eq(AdminRoleDept::getRoleId, roleId));
        if (request.dataScope() == 2 && request.deptIds() != null) {
            saveRoleDepts(roleId, request.deptIds());
        }

        // SEC-03：数据范围变更软失效——旧令牌按旧范围查询的授权立即失效
        invalidateRoleHolders(roleId);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        role.setStatus(status);
        adminRoleMapper.updateById(role);

        // SEC-03：停用角色 → 持有者软失效（旧令牌立即失效，刷新后不再获得该角色权限）；
        // 启用无需失效
        if (status != null && status == 1) {
            invalidateRoleHolders(id);
        }
    }

    private void checkRoleKeyUnique(String roleKey, Long excludeId) {
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<AdminRole>()
                .eq(AdminRole::getRoleKey, roleKey);
        AdminRole existing = adminRoleMapper.selectOne(wrapper);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("ROLE_KEY_EXISTS", "角色标识已存在");
        }
    }

    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            AdminRoleMenu roleMenu = new AdminRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            adminRoleMenuMapper.insert(roleMenu);
        }
    }

    private void saveRoleDepts(Long roleId, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        for (Long deptId : deptIds) {
            AdminRoleDept roleDept = new AdminRoleDept();
            roleDept.setRoleId(roleId);
            roleDept.setDeptId(deptId);
            adminRoleDeptMapper.insert(roleDept);
        }
    }

    private AdminRoleResponse toResponse(AdminRole role) {
        return adminConverter.toRoleResponse(role);
    }

    /** 软失效：递增认证状态版本（不撤销刷新家族），持有该角色的管理员下次刷新拿到新权限 */
    private void invalidateRoleHolders(Long roleId) {
        List<Long> userIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, roleId))
                .stream().map(AdminUserRole::getUserId).toList();
        for (Long userId : userIds) {
            authRevocationFeignClient.invalidateState(
                    AuthRevocationFeignClient.adminSoftInvalidate(userId));
        }
    }
}
