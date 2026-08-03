package com.cloudmart.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminLoginLogQueryRequest;
import com.cloudmart.admin.dto.AdminLoginLogResponse;

public interface AdminLoginLogService {

    Page<AdminLoginLogResponse> page(AdminLoginLogQueryRequest request);

    void recordLogin(String username, String ipaddr, String loginLocation, String browser, String os, Integer status, String msg);

    void delete(Long id);

    void clean();
}
