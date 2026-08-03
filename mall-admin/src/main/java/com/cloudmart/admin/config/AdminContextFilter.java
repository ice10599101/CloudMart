package com.cloudmart.admin.config;

import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.context.AdminSecurityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AdminContextFilter.class);

    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminMenuMapper adminMenuMapper;

    public AdminContextFilter(AdminUserRoleMapper adminUserRoleMapper,
                              AdminRoleMapper adminRoleMapper,
                              AdminRoleMenuMapper adminRoleMenuMapper,
                              AdminMenuMapper adminMenuMapper) {
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminRoleMenuMapper = adminRoleMenuMapper;
        this.adminMenuMapper = adminMenuMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            String userIdStr = httpRequest.getHeader(SecurityConstants.USER_ID_HEADER);
            if (userIdStr != null && !userIdStr.isEmpty()) {
                Long userId = Long.valueOf(userIdStr);
                String username = httpRequest.getHeader(SecurityConstants.ADMIN_USERNAME_HEADER);
                String role = httpRequest.getHeader(SecurityConstants.ADMIN_ROLE_HEADER);
                String permsStr = httpRequest.getHeader(SecurityConstants.ADMIN_PERMISSIONS_HEADER);
                String deptIdStr = httpRequest.getHeader(SecurityConstants.ADMIN_DEPT_ID_HEADER);

                Set<String> permissions = Collections.emptySet();
                if (permsStr != null && !permsStr.isEmpty()) {
                    permissions = Arrays.stream(permsStr.split(","))
                            .filter(p -> !p.isEmpty())
                            .collect(Collectors.toSet());
                }

                if (permissions.isEmpty()) {
                    permissions = resolvePermissionsFromDb(userId);
                    if (!permissions.isEmpty()) {
                        log.debug("JWT permissions empty for userId={}, resolved from DB: {}", userId, permissions);
                    }
                }

                Long deptId = deptIdStr != null && !deptIdStr.isEmpty() ? Long.valueOf(deptIdStr) : null;

                AdminSecurityContext.set(new AdminSecurityContext(userId, username, role, permissions, deptId));
            } else {
                log.warn("No X-User-Id header for {} {}, all headers: {}", httpRequest.getMethod(), httpRequest.getRequestURI(), httpRequest.getHeaderNames().hasMoreElements() ? String.join(", ", Collections.list(httpRequest.getHeaderNames())) : "NONE");
            }

            chain.doFilter(request, response);
        } finally {
            AdminSecurityContext.clear();
        }
    }

    private Set<String> resolvePermissionsFromDb(Long userId) {
        try {
            List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                    new LambdaQueryWrapper<AdminUserRole>()
                            .eq(AdminUserRole::getUserId, userId)
            );

            for (AdminUserRole userRole : userRoles) {
                AdminRole adminRole = adminRoleMapper.selectById(userRole.getRoleId());
                if (adminRole != null && "admin".equals(adminRole.getRoleKey())) {
                    return Set.of("*:*:*");
                }
            }

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
        } catch (Exception e) {
            log.warn("Failed to resolve permissions from DB for userId={}: {}", userId, e.getMessage());
            return Collections.emptySet();
        }
    }
}
