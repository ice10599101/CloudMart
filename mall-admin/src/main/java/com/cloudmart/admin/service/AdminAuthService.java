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

    public AdminUser validateCredentials(String username, String password) {
        log.info("validateCredentials called with username={}", username);

        List<AdminUser> allUsers = adminUserMapper.selectList(null);
        for (AdminUser u : allUsers) {
            log.info("DB user: id={}, username={}, status={}, deletedAt={}, passwordPrefix={}",
                    u.getId(), u.getUsername(), u.getStatus(), u.getDeletedAt(),
                    u.getPassword() != null ? u.getPassword().substring(0, Math.min(7, u.getPassword().length())) : "NULL");
        }

        AdminUser adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username)
                        .eq(AdminUser::getStatus, 1)
        );

        if (adminUser == null) {
            log.warn("validateCredentials: no user found with username={} and status=1", username);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        log.info("validateCredentials: found user id={}, username={}, status={}, passwordPrefix={}",
                adminUser.getId(), adminUser.getUsername(), adminUser.getStatus(),
                adminUser.getPassword() != null ? adminUser.getPassword().substring(0, Math.min(7, adminUser.getPassword().length())) : "NULL");

        if (!passwordEncoder.matches(password, adminUser.getPassword())) {
            log.warn("validateCredentials: password mismatch for username={}", username);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        log.info("validateCredentials: authentication successful for username={}", username);
        return adminUser;
    }

    public AdminUser getUserById(Long userId) {
        AdminUser adminUser = adminUserMapper.selectById(userId);
        if (adminUser == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return adminUser;
    }

    public boolean checkSuperAdmin(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getUserId, userId)
        );

        for (AdminUserRole userRole : userRoles) {
            AdminRole role = adminRoleMapper.selectById(userRole.getRoleId());
            if (role != null && "admin".equals(role.getRoleKey())) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getPermissionsByUserId(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getUserId, userId)
        );

        Set<String> permissions = new HashSet<>();
        Set<Long> menuIds = new HashSet<>();

        for (AdminUserRole userRole : userRoles) {
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
