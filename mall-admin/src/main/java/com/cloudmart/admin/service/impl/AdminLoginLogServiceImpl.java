package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminLoginLogQueryRequest;
import com.cloudmart.admin.dto.AdminLoginLogResponse;
import com.cloudmart.admin.entity.AdminLoginLog;
import com.cloudmart.admin.repository.AdminLoginLogMapper;
import com.cloudmart.admin.service.AdminLoginLogService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminLoginLogServiceImpl implements AdminLoginLogService {

    private final AdminLoginLogMapper adminLoginLogMapper;

    public AdminLoginLogServiceImpl(AdminLoginLogMapper adminLoginLogMapper) {
        this.adminLoginLogMapper = adminLoginLogMapper;
    }

    @Override
    public Page<AdminLoginLogResponse> page(AdminLoginLogQueryRequest request) {
        Page<AdminLoginLog> mpPage = new Page<>(request.page(), request.pageSize());
        LambdaQueryWrapper<AdminLoginLog> wrapper = new LambdaQueryWrapper<AdminLoginLog>()
                .like(request.username() != null && !request.username().isBlank(), AdminLoginLog::getUsername, request.username())
                .like(request.ipaddr() != null && !request.ipaddr().isBlank(), AdminLoginLog::getIpaddr, request.ipaddr())
                .eq(request.status() != null, AdminLoginLog::getStatus, request.status())
                .ge(request.beginTime() != null && !request.beginTime().isBlank(), AdminLoginLog::getLoginTime, request.beginTime())
                .le(request.endTime() != null && !request.endTime().isBlank(), AdminLoginLog::getLoginTime, request.endTime())
                .orderByDesc(AdminLoginLog::getLoginTime);

        Page<AdminLoginLog> result = adminLoginLogMapper.selectPage(mpPage, wrapper);

        Page<AdminLoginLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return responsePage;
    }

    @Override
    @Transactional
    public void recordLogin(String username, String ipaddr, String loginLocation, String browser, String os, Integer status, String msg) {
        AdminLoginLog loginLog = new AdminLoginLog();
        loginLog.setUsername(username);
        loginLog.setIpaddr(ipaddr);
        loginLog.setLoginLocation(loginLocation);
        loginLog.setBrowser(browser);
        loginLog.setOs(os);
        loginLog.setStatus(status);
        loginLog.setMsg(msg);
        loginLog.setLoginTime(LocalDateTime.now());
        adminLoginLogMapper.insert(loginLog);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminLoginLog log = adminLoginLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("LOGIN_LOG_NOT_FOUND", "登录日志不存在");
        }
        adminLoginLogMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void clean() {
        adminLoginLogMapper.delete(new LambdaQueryWrapper<>());
    }

    private AdminLoginLogResponse toResponse(AdminLoginLog log) {
        return new AdminLoginLogResponse(
                log.getId(),
                log.getUsername(),
                log.getIpaddr(),
                log.getLoginLocation(),
                log.getBrowser(),
                log.getOs(),
                log.getStatus(),
                log.getMsg(),
                log.getLoginTime()
        );
    }
}
