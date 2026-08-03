package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminDeptRequest;
import com.cloudmart.admin.dto.AdminDeptResponse;
import com.cloudmart.admin.entity.AdminDept;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.service.AdminDeptService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminDeptServiceImpl implements AdminDeptService {

    private final AdminDeptMapper adminDeptMapper;
    private final AdminUserMapper adminUserMapper;

    public AdminDeptServiceImpl(AdminDeptMapper adminDeptMapper,
                                AdminUserMapper adminUserMapper) {
        this.adminDeptMapper = adminDeptMapper;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public List<AdminDeptResponse> tree() {
        List<AdminDept> allDepts = adminDeptMapper.selectList(
                new LambdaQueryWrapper<AdminDept>().orderByAsc(AdminDept::getOrderNum)
        );
        return buildTree(allDepts.stream().map(this::toResponse).toList());
    }

    @Override
    public AdminDeptResponse getById(Long id) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
        return toResponse(dept);
    }

    @Override
    @Transactional
    public void create(AdminDeptRequest request) {
        AdminDept dept = new AdminDept();
        dept.setParentId(request.parentId());
        dept.setDeptName(request.deptName());
        dept.setOrderNum(request.orderNum());
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setStatus(request.status() != null ? request.status() : 0);
        dept.setAncestors(calculateAncestors(request.parentId()));
        adminDeptMapper.insert(dept);
    }

    @Override
    @Transactional
    public void update(Long id, AdminDeptRequest request) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }

        boolean parentChanged = !dept.getParentId().equals(request.parentId());
        dept.setParentId(request.parentId());
        dept.setDeptName(request.deptName());
        dept.setOrderNum(request.orderNum());
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setStatus(request.status());

        if (parentChanged) {
            dept.setAncestors(calculateAncestors(request.parentId()));
        }

        adminDeptMapper.updateById(dept);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }

        Long childCount = adminDeptMapper.selectCount(
                new LambdaQueryWrapper<AdminDept>().eq(AdminDept::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException("DEPT_HAS_CHILDREN", "存在子部门，无法删除");
        }

        Long userCount = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getDeptId, id)
        );
        if (userCount > 0) {
            throw new BusinessException("DEPT_HAS_USERS", "部门下存在用户，无法删除");
        }

        adminDeptMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminDept dept = adminDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
        dept.setStatus(status);
        adminDeptMapper.updateById(dept);
    }

    private String calculateAncestors(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        AdminDept parent = adminDeptMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("PARENT_DEPT_NOT_FOUND", "父部门不存在");
        }
        return parent.getAncestors() + "," + parentId;
    }

    private List<AdminDeptResponse> buildTree(List<AdminDeptResponse> allDepts) {
        Map<Long, List<AdminDeptResponse>> groupedByParent = allDepts.stream()
                .collect(Collectors.groupingBy(d -> d.parentId() != null ? d.parentId() : 0L));

        List<AdminDeptResponse> roots = new ArrayList<>();
        for (AdminDeptResponse dept : allDepts) {
            List<AdminDeptResponse> children = groupedByParent.get(dept.id());
            AdminDeptResponse node = new AdminDeptResponse(
                    dept.id(),
                    dept.parentId(),
                    dept.ancestors(),
                    dept.deptName(),
                    dept.orderNum(),
                    dept.leader(),
                    dept.phone(),
                    dept.email(),
                    dept.status(),
                    dept.createdAt(),
                    children != null ? children : List.of()
            );
            if (dept.parentId() == null || dept.parentId() == 0L) {
                roots.add(node);
            } else {
                List<AdminDeptResponse> siblings = groupedByParent.get(dept.parentId());
                if (siblings != null) {
                    int idx = siblings.indexOf(dept);
                    if (idx >= 0) {
                        siblings.set(idx, node);
                    }
                }
            }
        }
        return roots;
    }

    private AdminDeptResponse toResponse(AdminDept dept) {
        return new AdminDeptResponse(
                dept.getId(),
                dept.getParentId(),
                dept.getAncestors(),
                dept.getDeptName(),
                dept.getOrderNum(),
                dept.getLeader(),
                dept.getPhone(),
                dept.getEmail(),
                dept.getStatus(),
                dept.getCreatedAt(),
                List.of()
        );
    }
}
