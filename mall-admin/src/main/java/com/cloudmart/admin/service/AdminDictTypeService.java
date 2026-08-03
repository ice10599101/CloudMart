package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminDictTypeRequest;
import com.cloudmart.admin.dto.AdminDictTypeResponse;

import java.util.List;

public interface AdminDictTypeService {

    List<AdminDictTypeResponse> list();

    AdminDictTypeResponse getById(Long id);

    void create(AdminDictTypeRequest request);

    void update(Long id, AdminDictTypeRequest request);

    void delete(Long id);

    void refreshCache();

    void updateStatus(Long id, Integer status);
}
