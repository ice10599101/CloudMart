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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminMenuMapper adminMenuMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(AdminUserMapper adminUserMapper,
                            AdminUserRoleMapper adminUserRoleMapper,
                            AdminRoleMapper adminRoleMapper,
                            AdminRoleMenuMapper adminRoleMenuMapper,
                            AdminMenuMapper adminMenuMapper,
                            PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminRoleMenuMapper = adminRoleMenuMapper;
        this.adminMenuMapper = adminMenuMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * S04：凭据校验——规范化用户名经唯一索引定位（uk(username)），单行查询；
     * 全用户加载与哈希前缀诊断日志已移除（扩大暴露面与查询成本）。
     * 失败统一文案，不区分"用户不存在/密码错误"。
     */
    public AdminUser validateCredentials(String username, String password) {
        AdminUser adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username == null ? "" : username.trim())
                        .eq(AdminUser::getStatus, 1)
        );

        if (adminUser == null) {
            log.warn("validateCredentials: 账号不存在或已停用");
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        if (!passwordEncoder.matches(password, adminUser.getPassword())) {
            log.warn("validateCredentials: 凭据校验失败 userId={}", adminUser.getId());
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        return adminUser;
    }

    public AdminUser getUserById(Long userId) {
        AdminUser adminUser = adminUserMapper.selectById(userId);
        if (adminUser == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return adminUser;
    }

    /** S04：仅 activeRole（status=1）参与超管判定——禁用角色不得恢复特权 */
    public boolean checkSuperAdmin(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getUserId, userId)
        );

        for (AdminUserRole userRole : userRoles) {
            AdminRole role = adminRoleMapper.selectById(userRole.getRoleId());
            if (role != null && isActive(role) && "admin".equals(role.getRoleKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(AdminRole role) {
        return role.getStatus() != null && role.getStatus() == 1;
    }

    public Set<String> getPermissionsByUserId(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getUserId, userId)
        );

        Set<String> permissions = new HashSet<>();
        Set<Long> menuIds = new HashSet<>();

        for (AdminUserRole userRole : userRoles) {
            AdminRole role = adminRoleMapper.selectById(userRole.getRoleId());
            // S04：禁用角色的菜单权限不参与解析（刷新后不再获得禁用角色权限）
            if (role == null || !isActive(role)) {
                continue;
            }
            List<AdminRoleMenu> roleMenus = adminRoleMenuMapper.selectList(
                    new LambdaQueryWrapper<AdminRoleMenu>()
                            .eq(AdminRoleMenu::getRoleId, userRole.getRoleId())
            );
            for (AdminRoleMenu roleMenu : roleMenus) {
                menuIds.add(roleMenu.getMenuId());
            }
        }

        if (!menuIds.isEmpty()) {
            List<AdminMenu> menus = adminMenuMapper.selectByIds(menuIds);
            for (AdminMenu menu : menus) {
                if (menu.getPerms() != null && !menu.getPerms().isEmpty()) {
                    permissions.add(menu.getPerms());
                }
            }
        }

        return permissions;
    }

    public Set<String> resolvePermissions(Long userId) {
        boolean isSuperAdmin = checkSuperAdmin(userId);
        return isSuperAdmin ? Set.of("*:*:*") : getPermissionsByUserId(userId);
    }
}
