package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.AdminPostRequest;
import com.cloudmart.admin.dto.AdminPostResponse;

import java.util.List;

public interface AdminPostService {

    List<AdminPostResponse> list();

    AdminPostResponse getById(Long id);

    void create(AdminPostRequest request);

    void update(Long id, AdminPostRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
