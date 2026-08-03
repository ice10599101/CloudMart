package com.cloudmart.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.entity.AdminDept;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminRoleDept;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminRoleDeptMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.datascope.DataScopeHandler;
import com.cloudmart.common.datascope.DataScopeResult;
import com.cloudmart.common.datascope.DataScopeType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataScopeService implements DataScopeHandler {

    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleDeptMapper adminRoleDeptMapper;
    private final AdminDeptMapper adminDeptMapper;

    public DataScopeService(AdminUserRoleMapper adminUserRoleMapper,
                            AdminRoleMapper adminRoleMapper,
                            AdminRoleDeptMapper adminRoleDeptMapper,
                            AdminDeptMapper adminDeptMapper) {
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminRoleDeptMapper = adminRoleDeptMapper;
        this.adminDeptMapper = adminDeptMapper;
    }

    @Override
    public DataScopeResult resolveDataScope() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
        }
        return resolveDataScope(ctx.userId());
    }

    public DataScopeResult resolveDataScope(Long userId) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx != null && ctx.isSuperAdmin()) {
            return new DataScopeResult(DataScopeType.ALL, Collections.emptyList());
        }

        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
        }

        Set<Long> roleIds = userRoles.stream().map(AdminUserRole::getRoleId).collect(Collectors.toSet());
        List<AdminRole> roles = adminRoleMapper.selectByIds(roleIds).stream()
                .filter(r -> r.getDataScope() != null)
                .sorted(Comparator.comparing(AdminRole::getDataScope))
                .toList();

        for (AdminRole role : roles) {
            switch (role.getDataScope()) {
                case 1 -> {
                    return new DataScopeResult(DataScopeType.ALL, Collections.emptyList());
                }
                case 2 -> {
                    List<Long> deptIds = getCustomDeptIds(roleIds);
                    return new DataScopeResult(DataScopeType.CUSTOM, deptIds);
                }
                case 3 -> {
                    Long deptId = ctx != null ? ctx.deptId() : null;
                    return new DataScopeResult(DataScopeType.DEPT,
                            deptId != null ? List.of(deptId) : Collections.emptyList());
                }
                case 4 -> {
                    Long deptId = ctx != null ? ctx.deptId() : null;
                    if (deptId == null) {
                        return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
                    }
                    List<Long> deptIds = getDeptAndChildrenIds(deptId);
                    return new DataScopeResult(DataScopeType.DEPT_AND_CHILD, deptIds);
                }
                case 5 -> {
                    return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
                }
                default -> {}
            }
        }

        return new DataScopeResult(DataScopeType.SELF, Collections.emptyList());
    }

    private List<Long> getCustomDeptIds(Set<Long> roleIds) {
        List<AdminRoleDept> roleDepts = adminRoleDeptMapper.selectList(
                new LambdaQueryWrapper<AdminRoleDept>().in(AdminRoleDept::getRoleId, roleIds)
        );
        return roleDepts.stream().map(AdminRoleDept::getDeptId).distinct().collect(Collectors.toList());
    }

    private List<Long> getDeptAndChildrenIds(Long parentDeptId) {
        List<Long> result = new ArrayList<>();
        result.add(parentDeptId);
        collectChildDeptIds(parentDeptId, result);
        return result;
    }

    private void collectChildDeptIds(Long parentId, List<Long> result) {
        List<AdminDept> children = adminDeptMapper.selectList(
                new LambdaQueryWrapper<AdminDept>().eq(AdminDept::getParentId, parentId)
        );
        for (AdminDept child : children) {
            result.add(child.getId());
            collectChildDeptIds(child.getId(), result);
        }
    }
}
