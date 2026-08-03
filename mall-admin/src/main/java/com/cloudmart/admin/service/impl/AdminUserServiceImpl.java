package com.cloudmart.admin.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminPostResponse;
import com.cloudmart.admin.dto.AdminResetPwdRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.dto.AdminUserExcelDTO;
import com.cloudmart.admin.dto.AdminUserImportResult;
import com.cloudmart.admin.dto.AdminUserQueryRequest;
import com.cloudmart.admin.dto.AdminUserRequest;
import com.cloudmart.admin.dto.AdminUserResponse;
import com.cloudmart.admin.dto.AdminUserUpdateRequest;
import com.cloudmart.admin.entity.AdminDept;
import com.cloudmart.admin.entity.AdminPost;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.entity.AdminUserPost;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminPostMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminUserPostMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.cloudmart.admin.service.AdminUserService;
import com.cloudmart.admin.service.DataScopeService;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.datascope.DataScopeResult;
import com.cloudmart.common.datascope.DataScopeType;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final String DEFAULT_IMPORT_PASSWORD = "123456";

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminUserPostMapper adminUserPostMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPostMapper adminPostMapper;
    private final AdminDeptMapper adminDeptMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final AdminConverter adminConverter;

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper,
                                AdminUserRoleMapper adminUserRoleMapper,
                                AdminUserPostMapper adminUserPostMapper,
                                AdminRoleMapper adminRoleMapper,
                                AdminPostMapper adminPostMapper,
                                AdminDeptMapper adminDeptMapper,
                                PasswordEncoder passwordEncoder,
                                DataScopeService dataScopeService,
                                AdminConverter adminConverter) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminUserPostMapper = adminUserPostMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminPostMapper = adminPostMapper;
        this.adminDeptMapper = adminDeptMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
        this.adminConverter = adminConverter;
    }

    @Override
    public Page<AdminUserResponse> page(AdminUserQueryRequest request) {
        Page<AdminUser> mpPage = new Page<>(request.page(), request.pageSize());
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                .like(request.username() != null && !request.username().isBlank(), AdminUser::getUsername, request.username())
                .like(request.phone() != null && !request.phone().isBlank(), AdminUser::getPhone, request.phone())
                .eq(request.status() != null, AdminUser::getStatus, request.status())
                .eq(request.deptId() != null, AdminUser::getDeptId, request.deptId())
                .orderByDesc(AdminUser::getCreatedAt);

        applyDataScope(wrapper);

        Page<AdminUser> result = adminUserMapper.selectPage(mpPage, wrapper);

        Page<AdminUserResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return responsePage;
    }

    @Override
    public AdminUserResponse getById(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return toResponse(user);
    }

    @Override
    @Transactional
    public void create(AdminUserRequest request) {
        checkUsernameUnique(request.username(), null);

        AdminUser user = new AdminUser();
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setSex(request.sex());
        user.setAvatar(request.avatar());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDeptId(request.deptId());
        user.setStatus(request.status() != null ? request.status() : 0);
        user.setRemark(request.remark());
        adminUserMapper.insert(user);

        saveUserRoles(user.getId(), request.roleIds());
        saveUserPosts(user.getId(), request.postIds());
    }

    @Override
    @Transactional
    public void update(Long id, AdminUserUpdateRequest request) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        checkUsernameUnique(user.getUsername(), id);

        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setSex(request.sex());
        user.setAvatar(request.avatar());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        user.setRemark(request.remark());
        adminUserMapper.updateById(user);

        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, id));
        adminUserPostMapper.delete(new LambdaQueryWrapper<AdminUserPost>().eq(AdminUserPost::getUserId, id));
        saveUserRoles(id, request.roleIds());
        saveUserPosts(id, request.postIds());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        adminUserMapper.deleteById(id);
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, id));
        adminUserPostMapper.delete(new LambdaQueryWrapper<AdminUserPost>().eq(AdminUserPost::getUserId, id));
    }

    @Override
    @Transactional
    public void resetPassword(AdminResetPwdRequest request) {
        AdminUser user = adminUserMapper.selectById(request.userId());
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        adminUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        user.setStatus(status);
        adminUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId));
        saveUserRoles(userId, roleIds);
    }

    @Override
    public void exportUsers(AdminUserQueryRequest request, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("用户数据", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                    .like(request.username() != null && !request.username().isBlank(), AdminUser::getUsername, request.username())
                    .like(request.phone() != null && !request.phone().isBlank(), AdminUser::getPhone, request.phone())
                    .eq(request.status() != null, AdminUser::getStatus, request.status())
                    .eq(request.deptId() != null, AdminUser::getDeptId, request.deptId())
                    .orderByDesc(AdminUser::getCreatedAt);

            List<AdminUser> users = adminUserMapper.selectList(wrapper);
            List<AdminUserExcelDTO> excelData = users.stream().map(this::toExcelDTO).toList();

            EasyExcel.write(response.getOutputStream(), AdminUserExcelDTO.class)
                    .sheet("用户数据")
                    .doWrite(excelData);
        } catch (IOException e) {
            throw new BusinessException("EXPORT_FAILED", "导出用户数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public AdminUserImportResult importUsers(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("IMPORT_FAILED", "导入文件不能为空");
        }

        List<AdminUserExcelDTO> excelData;
        try {
            excelData = EasyExcel.read(file.getInputStream())
                    .head(AdminUserExcelDTO.class)
                    .sheet()
                    .doReadSync();
        } catch (IOException e) {
            throw new BusinessException("IMPORT_FAILED", "读取导入文件失败: " + e.getMessage());
        }

        int successCount = 0;
        int failureCount = 0;
        List<String> failureMessages = new ArrayList<>();

        for (int i = 0; i < excelData.size(); i++) {
            int rowNum = i + 2;
            AdminUserExcelDTO dto = excelData.get(i);
            try {
                if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                    failureMessages.add("第" + rowNum + "行: 用户名不能为空");
                    failureCount++;
                    continue;
                }
                checkUsernameUnique(dto.getUsername(), null);

                AdminUser user = new AdminUser();
                user.setUsername(dto.getUsername());
                user.setNickname(dto.getNickname());
                user.setEmail(dto.getEmail());
                user.setPhone(dto.getPhone());
                user.setSex(dto.getSex());
                user.setDeptId(dto.getDeptId());
                user.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
                user.setRemark(dto.getRemark());
                user.setPassword(passwordEncoder.encode(DEFAULT_IMPORT_PASSWORD));
                adminUserMapper.insert(user);
                successCount++;
            } catch (BusinessException e) {
                failureMessages.add("第" + rowNum + "行: " + e.getMessage());
                failureCount++;
            }
        }

        return new AdminUserImportResult(successCount, failureCount, failureMessages);
    }

    private void applyDataScope(LambdaQueryWrapper<AdminUser> wrapper) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            return;
        }
        if (ctx.isSuperAdmin()) {
            return;
        }

        DataScopeResult dataScope = dataScopeService.resolveDataScope(ctx.userId());
        switch (dataScope.type()) {
            case ALL -> {}
            case CUSTOM -> {
                if (dataScope.deptIds() == null || dataScope.deptIds().isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(AdminUser::getDeptId, dataScope.deptIds());
                }
            }
            case DEPT -> {
                if (ctx.deptId() == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.eq(AdminUser::getDeptId, ctx.deptId());
                }
            }
            case DEPT_AND_CHILD -> {
                if (dataScope.deptIds() == null || dataScope.deptIds().isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(AdminUser::getDeptId, dataScope.deptIds());
                }
            }
            case SELF -> wrapper.eq(AdminUser::getId, ctx.userId());
        }
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username);
        AdminUser existing = adminUserMapper.selectOne(wrapper);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
        }
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            AdminUserRole userRole = new AdminUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            adminUserRoleMapper.insert(userRole);
        }
    }

    private void saveUserPosts(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            AdminUserPost userPost = new AdminUserPost();
            userPost.setUserId(userId);
            userPost.setPostId(postId);
            adminUserPostMapper.insert(userPost);
        }
    }

    private AdminUserResponse toResponse(AdminUser user) {
        String deptName = null;
        if (user.getDeptId() != null) {
            AdminDept dept = adminDeptMapper.selectById(user.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }

        List<AdminRoleResponse> roles = getRolesByUserId(user.getId());
        List<AdminPostResponse> posts = getPostsByUserId(user.getId());

        AdminUserResponse response = adminConverter.toUserResponse(user);
        return new AdminUserResponse(
                response.id(),
                response.username(),
                response.nickname(),
                response.email(),
                response.phone(),
                response.sex(),
                response.avatar(),
                response.deptId(),
                deptName,
                response.status(),
                response.remark(),
                response.loginIp(),
                response.loginDate(),
                response.pwdUpdateDate(),
                response.createdAt(),
                roles,
                posts
        );
    }

    private AdminUserExcelDTO toExcelDTO(AdminUser user) {
        return new AdminUserExcelDTO(
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getSex(),
                user.getDeptId(),
                user.getStatus(),
                user.getRemark()
        );
    }

    private List<AdminRoleResponse> getRolesByUserId(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(AdminUserRole::getRoleId).toList();
        return adminRoleMapper.selectByIds(roleIds).stream()
                .map(this::toRoleResponse)
                .toList();
    }

    private List<AdminPostResponse> getPostsByUserId(Long userId) {
        List<AdminUserPost> userPosts = adminUserPostMapper.selectList(
                new LambdaQueryWrapper<AdminUserPost>().eq(AdminUserPost::getUserId, userId)
        );
        if (userPosts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> postIds = userPosts.stream().map(AdminUserPost::getPostId).toList();
        return adminPostMapper.selectByIds(postIds).stream()
                .map(this::toPostResponse)
                .toList();
    }

    private AdminRoleResponse toRoleResponse(AdminRole role) {
        return adminConverter.toRoleResponse(role);
    }

    private AdminPostResponse toPostResponse(AdminPost post) {
        return adminConverter.toPostResponse(post);
    }
}
