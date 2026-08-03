package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminMenuRequest;
import com.cloudmart.admin.dto.AdminMenuResponse;

import java.util.List;

public interface AdminMenuService {

    List<AdminMenuResponse> tree();

    List<AdminMenuResponse> listByRoleId(Long roleId);

    void create(AdminMenuRequest request);

    void update(Long id, AdminMenuRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
