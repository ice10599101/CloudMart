package com.cloudmart.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminResetPwdRequest;
import com.cloudmart.admin.dto.AdminUserImportResult;
import com.cloudmart.admin.dto.AdminUserQueryRequest;
import com.cloudmart.admin.dto.AdminUserRequest;
import com.cloudmart.admin.dto.AdminUserResponse;
import com.cloudmart.admin.dto.AdminUserUpdateRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminUserService {

    Page<AdminUserResponse> page(AdminUserQueryRequest request);

    AdminUserResponse getById(Long id);

    void create(AdminUserRequest request);

    void update(Long id, AdminUserUpdateRequest request);

    void delete(Long id);

    void resetPassword(AdminResetPwdRequest request);

    void updateStatus(Long id, Integer status);

    void assignRoles(Long userId, List<Long> roleIds);

    void exportUsers(AdminUserQueryRequest request, HttpServletResponse response);

    AdminUserImportResult importUsers(MultipartFile file);
}
