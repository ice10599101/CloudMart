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
import com.cloudmart.admin.feign.AuthRevocationFeignClient;
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

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminUserPostMapper adminUserPostMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPostMapper adminPostMapper;
    private final AdminDeptMapper adminDeptMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final AdminConverter adminConverter;
    private final AuthRevocationFeignClient authRevocationFeignClient;
    private final AdminAuthorizationPolicy authorizationPolicy;

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper,
                                AdminUserRoleMapper adminUserRoleMapper,
                                AdminUserPostMapper adminUserPostMapper,
                                AdminRoleMapper adminRoleMapper,
                                AdminPostMapper adminPostMapper,
                                AdminDeptMapper adminDeptMapper,
                                PasswordEncoder passwordEncoder,
                                DataScopeService dataScopeService,
                                AdminConverter adminConverter,
                                AuthRevocationFeignClient authRevocationFeignClient,
                                AdminAuthorizationPolicy authorizationPolicy) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminUserPostMapper = adminUserPostMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminPostMapper = adminPostMapper;
        this.adminDeptMapper = adminDeptMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
        this.adminConverter = adminConverter;
        this.authRevocationFeignClient = authRevocationFeignClient;
        this.authorizationPolicy = authorizationPolicy;
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
        // SEC-03：读操作同样受数据范围约束，越界按不存在处理（不泄露记录存在性）
        assertWithinDataScope(user);
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
        assertWithinDataScope(user);

        checkUsernameUnique(user.getUsername(), id);

        boolean disabling = request.status() != null && request.status() == 1
                && (user.getStatus() == null || user.getStatus() != 1);
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

        // SEC-03：角色/状态变更统一触发认证失效——禁用硬失效（令牌立即不可用），
        // 其余变更软失效（刷新后拿新权限集）；失败向上抛出保证管理员可感知
        if (disabling) {
            authRevocationFeignClient.invalidateState(
                    AuthRevocationFeignClient.adminHardInvalidate(id));
        } else {
            authRevocationFeignClient.invalidateState(
                    AuthRevocationFeignClient.adminSoftInvalidate(id));
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        assertWithinDataScope(user);
        assertNotBuiltInSuperAdmin(user, "删除");

        // SEC-03：删除账号先硬失效其全部令牌（失败则中止，避免已删账号令牌仍流通）
        authRevocationFeignClient.invalidateState(
                AuthRevocationFeignClient.adminHardInvalidate(id));
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
        // S03：重置密码属提权路径——数据范围 + 超管目标保护（QA21：受限管理员重置范围外/超管被拒绝）
        authorizationPolicy.assertCanManage(user, "重置密码");
        // SEC-03：重置密码先失效认证状态（硬失效），失败则中止
        authRevocationFeignClient.invalidateState(
                AuthRevocationFeignClient.adminHardInvalidate(request.userId()));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        int updated = adminUserMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException("USER_STATE_CONFLICT", "用户状态已变更，请刷新重试");
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        // S03：数据范围 + 超管目标保护（QA21：受限管理员禁用范围外/超管被拒绝）
        authorizationPolicy.assertCanManage(user, "禁用或启用");
        boolean disabling = status != null && status != 1;
        if (disabling) {
            // 与删除同级保护：内置超管不可禁用；并兜底"至少保留一名可用超管"
            assertNotBuiltInSuperAdmin(user, "禁用");
            authorizationPolicy.assertNotLastEnabledSuperAdmin(user, status);
        }
        // SEC-03：禁用先失效认证状态（硬失效），失败则中止；启用无需失效
        if (disabling) {
            authRevocationFeignClient.invalidateState(
                    AuthRevocationFeignClient.adminHardInvalidate(id));
        }
        user.setStatus(status);
        int updated = adminUserMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException("USER_STATE_CONFLICT", "用户状态已变更，请刷新重试");
        }
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        assertWithinDataScope(user);

        // SEC-03：授予/保留内置超管角色仅限超管操作者；非超管不得改动超管的角色集
        AdminRole superRole = findBuiltInSuperAdminRole();
        if (superRole != null) {
            boolean grantsSuperRole = roleIds != null && roleIds.contains(superRole.getId());
            boolean hadSuperRole = hasBuiltInSuperAdminRole(userId);
            if ((grantsSuperRole || hadSuperRole) && !isSuperAdminOperator()) {
                throw new BusinessException("FORBIDDEN", "内置超级管理员角色仅超级管理员可分配或变更");
            }
        }

        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId));
        saveUserRoles(userId, roleIds);

        // SEC-03：角色集变更软失效，目标旧令牌立即失效
        authRevocationFeignClient.invalidateState(
                AuthRevocationFeignClient.adminSoftInvalidate(userId));
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

            // SEC-03：导出与分页同一数据范围约束，防止绕过分页直接全量导出
            applyDataScope(wrapper);

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
        List<String> initialCredentials = new ArrayList<>();

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
                // SEC-03：每人独立随机初始密码（一次性展示给操作者分发），
                // 不再使用统一固定初始密码
                String initialPassword = generateInitialPassword();
                user.setPassword(passwordEncoder.encode(initialPassword));
                adminUserMapper.insert(user);
                initialCredentials.add(dto.getUsername() + "=" + initialPassword);
                successCount++;
            } catch (BusinessException e) {
                failureMessages.add("第" + rowNum + "行: " + e.getMessage());
                failureCount++;
            }
        }

        return new AdminUserImportResult(successCount, failureCount, failureMessages, initialCredentials);
    }

    private void applyDataScope(LambdaQueryWrapper<AdminUser> wrapper) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null || ctx.isSuperAdmin()) {
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

    /**
     * SEC-03：单条读/写/删除的数据范围校验（getById/update/delete 不走 wrapper 查询，
     * 需要与 {@link #applyDataScope} 同一语义）；越界按资源不存在处理。
     */
    /** S03：数据范围校验集中到 {@link AdminAuthorizationPolicy}（与角色/授权上限同一权威） */
    private void assertWithinDataScope(AdminUser target) {
        authorizationPolicy.assertWithinDataScope(target);
    }

    /** 目标持有内置超管角色（roleKey=admin）时不允许删除/停用 */
    private void assertNotBuiltInSuperAdmin(AdminUser target, String action) {
        authorizationPolicy.assertNotBuiltInSuperAdmin(target, action);
    }

    private boolean isSuperAdminOperator() {
        return authorizationPolicy.isSuperAdminOperator();
    }

    private boolean hasBuiltInSuperAdminRole(Long userId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, userId));
        AdminRole superRole = findBuiltInSuperAdminRole();
        return superRole != null && userRoles.stream().anyMatch(ur -> superRole.getId().equals(ur.getRoleId()));
    }

    /** 内置超级管理员角色：roleKey = admin */
    private AdminRole findBuiltInSuperAdminRole() {
        return adminRoleMapper.selectOne(
                new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getRoleKey, "admin"));
    }

    /** 12 位随机初始密码：大小写字母 + 数字，SecureRandom 生成 */
    private String generateInitialPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return password.toString();
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
