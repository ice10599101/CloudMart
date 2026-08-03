package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminMenuRequest;
import com.cloudmart.admin.dto.AdminMenuResponse;
import com.cloudmart.admin.entity.AdminMenu;
import com.cloudmart.admin.entity.AdminRoleMenu;
import com.cloudmart.admin.repository.AdminMenuMapper;
import com.cloudmart.admin.repository.AdminRoleMenuMapper;
import com.cloudmart.admin.service.AdminMenuService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private final AdminMenuMapper adminMenuMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;

    public AdminMenuServiceImpl(AdminMenuMapper adminMenuMapper,
                                AdminRoleMenuMapper adminRoleMenuMapper) {
        this.adminMenuMapper = adminMenuMapper;
        this.adminRoleMenuMapper = adminRoleMenuMapper;
    }

    @Override
    public List<AdminMenuResponse> tree() {
        List<AdminMenu> allMenus = adminMenuMapper.selectList(
                new LambdaQueryWrapper<AdminMenu>().orderByAsc(AdminMenu::getOrderNum)
        );
        return buildTree(allMenus.stream().map(this::toResponse).toList());
    }

    @Override
    public List<AdminMenuResponse> listByRoleId(Long roleId) {
        List<AdminRoleMenu> roleMenus = adminRoleMenuMapper.selectList(
                new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, roleId)
        );
        if (roleMenus.isEmpty()) {
            return List.of();
        }

        Set<Long> menuIds = roleMenus.stream().map(AdminRoleMenu::getMenuId).collect(Collectors.toSet());
        List<AdminMenu> menus = adminMenuMapper.selectByIds(menuIds);
        return menus.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void create(AdminMenuRequest request) {
        AdminMenu menu = new AdminMenu();
        menu.setMenuName(request.menuName());
        menu.setParentId(request.parentId());
        menu.setOrderNum(request.orderNum());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setQuery(request.query());
        menu.setRouteName(request.routeName());
        menu.setIsFrame(request.isFrame());
        menu.setIsCache(request.isCache());
        menu.setMenuType(request.menuType());
        menu.setVisible(request.visible());
        menu.setStatus(request.status() != null ? request.status() : 0);
        menu.setPerms(request.perms());
        menu.setIcon(request.icon());
        menu.setRemark(request.remark());
        adminMenuMapper.insert(menu);
    }

    @Override
    @Transactional
    public void update(Long id, AdminMenuRequest request) {
        AdminMenu menu = adminMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        }

        menu.setMenuName(request.menuName());
        menu.setParentId(request.parentId());
        menu.setOrderNum(request.orderNum());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setQuery(request.query());
        menu.setRouteName(request.routeName());
        menu.setIsFrame(request.isFrame());
        menu.setIsCache(request.isCache());
        menu.setMenuType(request.menuType());
        menu.setVisible(request.visible());
        menu.setStatus(request.status());
        menu.setPerms(request.perms());
        menu.setIcon(request.icon());
        menu.setRemark(request.remark());
        adminMenuMapper.updateById(menu);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminMenu menu = adminMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        }

        Long childCount = adminMenuMapper.selectCount(
                new LambdaQueryWrapper<AdminMenu>().eq(AdminMenu::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException("MENU_HAS_CHILDREN", "存在子菜单，无法删除");
        }

        adminMenuMapper.deleteById(id);
        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getMenuId, id));
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminMenu menu = adminMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        }
        menu.setStatus(status);
        adminMenuMapper.updateById(menu);
    }

    private List<AdminMenuResponse> buildTree(List<AdminMenuResponse> allMenus) {
        Map<Long, List<AdminMenuResponse>> groupedByParent = allMenus.stream()
                .collect(Collectors.groupingBy(m -> m.parentId() != null ? m.parentId() : 0L));

        List<AdminMenuResponse> roots = new ArrayList<>();
        for (AdminMenuResponse menu : allMenus) {
            List<AdminMenuResponse> children = groupedByParent.get(menu.id());
            AdminMenuResponse node = new AdminMenuResponse(
                    menu.id(),
                    menu.menuName(),
                    menu.parentId(),
                    menu.orderNum(),
                    menu.path(),
                    menu.component(),
                    menu.query(),
                    menu.routeName(),
                    menu.isFrame(),
                    menu.isCache(),
                    menu.menuType(),
                    menu.visible(),
                    menu.status(),
                    menu.perms(),
                    menu.icon(),
                    menu.remark(),
                    menu.createdAt(),
                    children != null ? children : List.of()
            );
            if (menu.parentId() == null || menu.parentId() == 0L) {
                roots.add(node);
            } else {
                List<AdminMenuResponse> siblings = groupedByParent.get(menu.parentId());
                if (siblings != null) {
                    int idx = siblings.indexOf(menu);
                    if (idx >= 0) {
                        siblings.set(idx, node);
                    }
                }
            }
        }
        return roots;
    }

    private AdminMenuResponse toResponse(AdminMenu menu) {
        return new AdminMenuResponse(
                menu.getId(),
                menu.getMenuName(),
                menu.getParentId(),
                menu.getOrderNum(),
                menu.getPath(),
                menu.getComponent(),
                menu.getQuery(),
                menu.getRouteName(),
                menu.getIsFrame(),
                menu.getIsCache(),
                menu.getMenuType(),
                menu.getVisible(),
                menu.getStatus(),
                menu.getPerms(),
                menu.getIcon(),
                menu.getRemark(),
                menu.getCreatedAt(),
                List.of()
        );
    }
}
