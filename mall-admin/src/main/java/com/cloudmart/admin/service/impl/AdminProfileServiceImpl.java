package com.cloudmart.admin.service.impl;

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
import com.cloudmart.admin.service.AdminProfileService;
import com.cloudmart.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminProfileServiceImpl implements AdminProfileService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminMenuMapper adminMenuMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminProfileServiceImpl(AdminUserMapper adminUserMapper,
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

    @Override
    public AdminProfileResponse getProfile(Long userId) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        boolean isSuperAdmin = checkSuperAdmin(userId);
        Set<String> permissions = isSuperAdmin ? Set.of("*:*:*") : getPermissionsByUserId(userId);
        Set<String> roles = getRolesByUserId(userId);

        return new AdminProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getDeptId(),
                user.getStatus(),
                user.getCreatedAt(),
                permissions,
                roles
        );
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, String nickname, String email, String phone, String avatar) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        adminUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(user);
    }

    private boolean checkSuperAdmin(Long userId) {
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

    private Set<String> getPermissionsByUserId(Long userId) {
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
            List<AdminMenu> menus = adminMenuMapper.selectBatchIds(menuIds);
            for (AdminMenu menu : menus) {
                if (menu.getPerms() != null && !menu.getPerms().isEmpty()) {
                    permissions.add(menu.getPerms());
                }
            }
        }

        return permissions;
    }

    private Set<String> getRolesByUserId(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getUserId, userId)
        );

        Set<String> roles = new HashSet<>();
        for (AdminUserRole userRole : userRoles) {
            AdminRole role = adminRoleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                roles.add(role.getRoleKey());
            }
        }
        return roles;
    }
}
