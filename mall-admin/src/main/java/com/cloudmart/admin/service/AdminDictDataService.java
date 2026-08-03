package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminDictDataRequest;
import com.cloudmart.admin.dto.AdminDictDataResponse;

import java.util.List;

public interface AdminDictDataService {

    List<AdminDictDataResponse> listByType(String dictType);

    AdminDictDataResponse getById(Long id);

    void create(AdminDictDataRequest request);

    void update(Long id, AdminDictDataRequest request);

    void delete(Long id);

    void refreshCache(String dictType);

    void updateStatus(Long id, Integer status);
}
