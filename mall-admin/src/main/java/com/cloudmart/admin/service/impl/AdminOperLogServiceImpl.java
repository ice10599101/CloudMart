package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminOperLogQueryRequest;
import com.cloudmart.admin.dto.AdminOperLogResponse;
import com.cloudmart.admin.entity.AdminOperLog;
import com.cloudmart.admin.repository.AdminOperLogMapper;
import com.cloudmart.admin.service.AdminOperLogService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperLogServiceImpl implements AdminOperLogService {

    private final AdminOperLogMapper adminOperLogMapper;

    public AdminOperLogServiceImpl(AdminOperLogMapper adminOperLogMapper) {
        this.adminOperLogMapper = adminOperLogMapper;
    }

    @Override
    public void save(AdminOperLog adminOperLog) {
        adminOperLogMapper.insert(adminOperLog);
    }

    @Override
    public Page<AdminOperLogResponse> page(AdminOperLogQueryRequest request) {
        Page<AdminOperLog> mpPage = new Page<>(request.page(), request.pageSize());
        LambdaQueryWrapper<AdminOperLog> wrapper = new LambdaQueryWrapper<AdminOperLog>()
                .like(request.title() != null && !request.title().isBlank(), AdminOperLog::getTitle, request.title())
                .eq(request.businessType() != null, AdminOperLog::getBusinessType, request.businessType())
                .eq(request.status() != null, AdminOperLog::getStatus, request.status())
                .like(request.operName() != null && !request.operName().isBlank(), AdminOperLog::getOperName, request.operName())
                .ge(request.beginTime() != null && !request.beginTime().isBlank(), AdminOperLog::getOperTime, request.beginTime())
                .le(request.endTime() != null && !request.endTime().isBlank(), AdminOperLog::getOperTime, request.endTime())
                .orderByDesc(AdminOperLog::getOperTime);

        Page<AdminOperLog> result = adminOperLogMapper.selectPage(mpPage, wrapper);

        Page<AdminOperLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return responsePage;
    }

    @Override
    public AdminOperLogResponse getById(Long id) {
        AdminOperLog log = adminOperLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("OPER_LOG_NOT_FOUND", "操作日志不存在");
        }
        return toResponse(log);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminOperLog log = adminOperLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("OPER_LOG_NOT_FOUND", "操作日志不存在");
        }
        adminOperLogMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void clean() {
        adminOperLogMapper.delete(new LambdaQueryWrapper<>());
    }

    private AdminOperLogResponse toResponse(AdminOperLog log) {
        return new AdminOperLogResponse(
                log.getId(),
                log.getTitle(),
                log.getBusinessType(),
                log.getMethod(),
                log.getRequestMethod(),
                log.getOperatorType(),
                log.getOperUserId(),
                log.getOperName(),
                log.getDeptName(),
                log.getOperUrl(),
                log.getOperIp(),
                log.getOperLocation(),
                log.getOperParam(),
                log.getJsonResult(),
                log.getStatus(),
                log.getErrorMsg(),
                log.getOperTime(),
                log.getCostTime()
        );
    }
}
