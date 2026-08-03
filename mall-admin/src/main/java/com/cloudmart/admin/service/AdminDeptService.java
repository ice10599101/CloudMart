package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminDeptRequest;
import com.cloudmart.admin.dto.AdminDeptResponse;

import java.util.List;

public interface AdminDeptService {

    List<AdminDeptResponse> tree();

    AdminDeptResponse getById(Long id);

    void create(AdminDeptRequest request);

    void update(Long id, AdminDeptRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
