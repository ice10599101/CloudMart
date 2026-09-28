package com.cloudmart.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.context.AdminSecurityContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员上下文补全过滤器（SEC-01/03）。
 *
 * <p>身份来源只有已验签的 admin JWT：{@code UserJwtAuthenticationFilter} 在
 * Security Filter Chain 中验签后已填充 {@code AdminSecurityContext}（userId/
 * username/role/permissions/deptId 来自令牌声明）。本过滤器不再读取任何
 * {@code X-User-Id}/{@code X-Admin-*} 头——直连管理服务时裸头不构成任何信任。</p>
 *
 * <p>唯一职责：令牌权限声明为空时，从数据库解析该管理员的实际权限集补全
 * （权限码由菜单表维护，签发后角色调整在版本校验之外再兜一层）。
 * 非 admin 令牌（USER/SERVICE）不产生管理上下文。</p>
 */
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
        try {
            AdminSecurityContext context = AdminSecurityContext.get();
            if (context != null && (context.permissions() == null || context.permissions().isEmpty())) {
                Set<String> permissions = resolvePermissionsFromDb(context.userId());
                if (!permissions.isEmpty()) {
                    // 重建上下文：令牌未携带权限声明时按 DB 权威补全
                    AdminSecurityContext.set(new AdminSecurityContext(
                            context.userId(), context.username(), context.role(),
                            permissions, context.deptId()));
                }
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
