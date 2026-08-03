package com.cloudmart.admin.listener;

import com.cloudmart.admin.entity.AdminOperLog;
import com.cloudmart.admin.service.AdminOperLogService;
import com.cloudmart.common.event.OperLogEvent;
import com.cloudmart.common.model.OperLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OperLogEventListener {

    private static final Logger log = LoggerFactory.getLogger(OperLogEventListener.class);

    private final AdminOperLogService adminOperLogService;

    public OperLogEventListener(AdminOperLogService adminOperLogService) {
        this.adminOperLogService = adminOperLogService;
    }

    @Async
    @EventListener
    public void onOperLogEvent(OperLogEvent event) {
        try {
            OperLogRecord record = event.getRecord();
            AdminOperLog entity = new AdminOperLog();
            entity.setTitle(nullToEmpty(record.getTitle()));
            entity.setBusinessType(record.getBusinessType());
            entity.setOperatorType(record.getOperatorType());
            entity.setOperUserId(record.getOperUserId());
            entity.setOperName(nullToEmpty(record.getOperName()));
            entity.setMethod(nullToEmpty(record.getMethod()));
            entity.setRequestMethod(nullToEmpty(record.getRequestMethod()));
            entity.setOperUrl(nullToEmpty(record.getOperUrl()));
            entity.setOperIp(nullToEmpty(record.getOperIp()));
            entity.setOperLocation("");
            entity.setOperParam(record.getOperParam());
            entity.setJsonResult(record.getJsonResult());
            entity.setStatus(record.getStatus());
            entity.setErrorMsg(record.getErrorMsg());
            entity.setCostTime(record.getCostTime());
            entity.setOperTime(LocalDateTime.now());
            adminOperLogService.save(entity);
            log.debug("Operation log persisted: title={}, operName={}", record.getTitle(), record.getOperName());
        } catch (Exception e) {
            log.error("Failed to persist operation log: {}", e.getMessage(), e);
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
