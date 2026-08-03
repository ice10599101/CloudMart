package com.cloudmart.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminOperLogQueryRequest;
import com.cloudmart.admin.dto.AdminOperLogResponse;
import com.cloudmart.admin.entity.AdminOperLog;

public interface AdminOperLogService {

    void save(AdminOperLog adminOperLog);

    Page<AdminOperLogResponse> page(AdminOperLogQueryRequest request);

    AdminOperLogResponse getById(Long id);

    void delete(Long id);

    void clean();
}
