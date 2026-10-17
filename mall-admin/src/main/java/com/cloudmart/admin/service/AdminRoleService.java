package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminRoleMenuRequest;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;

import java.util.List;

public interface AdminRoleService {

    List<AdminRoleResponse> list();

    AdminRoleResponse getById(Long id);

    void create(AdminRoleRequest request);

    void update(Long id, AdminRoleRequest request);

    void delete(Long id);

    void assignMenus(AdminRoleMenuRequest request);


    List<Long> getMenuIdsByRoleId(Long roleId);


    void updateStatus(Long id, Integer status);
}
