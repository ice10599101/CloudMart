package com.cloudmart.admin.config;

import com.cloudmart.admin.entity.AdminDept;
import com.cloudmart.admin.entity.AdminRole;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.entity.AdminUserRole;
import com.cloudmart.admin.repository.AdminDeptMapper;
import com.cloudmart.admin.repository.AdminRoleMapper;
import com.cloudmart.admin.repository.AdminUserMapper;
import com.cloudmart.admin.repository.AdminUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminDataInitializer.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_NICKNAME = "超级管理员";
    private static final String SUPER_ADMIN_ROLE_KEY = "admin";
    private static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";
    private static final String DEFAULT_DEPT_NAME = "总公司";

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminDeptMapper adminDeptMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminDataInitializer(AdminUserMapper adminUserMapper,
                                AdminRoleMapper adminRoleMapper,
                                AdminDeptMapper adminDeptMapper,
                                AdminUserRoleMapper adminUserRoleMapper,
                                PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminDeptMapper = adminDeptMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long userCount = adminUserMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            log.info("Admin data already exists (count={}), skipping initialization", userCount);
            return;
        }

        log.info("No admin users found, initializing default admin data...");

        Long deptId = ensureDefaultDept();
        Long roleId = ensureSuperAdminRole();
        Long userId = createAdminUser(deptId);
        assignRole(userId, roleId);

        log.info("Default admin initialized - username: {}, password: {}", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }

    private Long ensureDefaultDept() {
        AdminDept existing = adminDeptMapper.selectOne(
                new LambdaQueryWrapper<AdminDept>().eq(AdminDept::getDeptName, DEFAULT_DEPT_NAME)
        );
        if (existing != null) {
            return existing.getId();
        }

        AdminDept dept = new AdminDept();
        dept.setParentId(0L);
        dept.setAncestors("0");
        dept.setDeptName(DEFAULT_DEPT_NAME);
        dept.setOrderNum(1);
        dept.setStatus(1);
        adminDeptMapper.insert(dept);
        log.info("Created default department: {}", DEFAULT_DEPT_NAME);
        return dept.getId();
    }

    private Long ensureSuperAdminRole() {
        AdminRole existing = adminRoleMapper.selectOne(
                new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getRoleKey, SUPER_ADMIN_ROLE_KEY)
        );
        if (existing != null) {
            return existing.getId();
        }

        AdminRole role = new AdminRole();
        role.setRoleName(SUPER_ADMIN_ROLE_NAME);
        role.setRoleKey(SUPER_ADMIN_ROLE_KEY);
        role.setRoleSort(1);
        role.setDataScope(1);
        role.setMenuCheckStrictly(1);
        role.setDeptCheckStrictly(1);
        role.setStatus(1);
        role.setRemark("系统内置超级管理员角色");
        adminRoleMapper.insert(role);
        log.info("Created super admin role: {}", SUPER_ADMIN_ROLE_KEY);
        return role.getId();
    }

    private Long createAdminUser(Long deptId) {
        AdminUser user = new AdminUser();
        user.setUsername(DEFAULT_ADMIN_USERNAME);
        user.setNickname(DEFAULT_ADMIN_NICKNAME);
        user.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        user.setDeptId(deptId);
        user.setStatus(1);
        user.setSex(2);
        user.setRemark("系统内置超级管理员");
        adminUserMapper.insert(user);
        log.info("Created default admin user: {}", DEFAULT_ADMIN_USERNAME);
        return user.getId();
    }

    private void assignRole(Long userId, Long roleId) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        adminUserRoleMapper.insert(userRole);
        log.info("Assigned super admin role to user {}", userId);
    }
}
