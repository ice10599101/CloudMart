package com.cloudmart.common.aspect;

import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.event.OperLogEvent;
import com.cloudmart.common.model.OperLogRecord;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    private static final int MAX_PARAM_LENGTH = 2000;

    private final ApplicationEventPublisher eventPublisher;

    public OperLogAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        log.info("OperLogAspect initialized successfully - AOP operation logging is active");
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        OperLogRecord record = new OperLogRecord();
        record.setTitle(operLog.title());
        record.setBusinessType(operLog.businessType());
        record.setOperatorType(operLog.operatorType());

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        record.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());

        AdminSecurityContext context = AdminSecurityContext.get();
        if (context != null) {
            record.setOperName(context.username());
            record.setOperUserId(context.userId());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            record.setRequestMethod(request.getMethod());
            record.setOperUrl(request.getRequestURI());
            record.setOperIp(getRemoteIp(request));
        }

        if (operLog.isSaveRequestData()) {
            record.setOperParam(buildRequestParam(joinPoint));
        }

        Object result;
        try {
            result = joinPoint.proceed();
            record.setStatus(0);
            if (operLog.isSaveResponseData() && result != null) {
                String jsonResult = truncate(String.valueOf(result), MAX_PARAM_LENGTH);
                record.setJsonResult(jsonResult);
            }
        } catch (Exception e) {
            record.setStatus(1);
            record.setErrorMsg(truncate(e.getMessage(), MAX_PARAM_LENGTH));
            throw e;
        } finally {
            record.setCostTime(System.currentTimeMillis() - startTime);
            publishOperLog(record);
        }

        return result;
    }

    private String buildRequestParam(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            if (paramValues[i] instanceof HttpServletRequest
                    || paramValues[i] instanceof HttpServletResponse
                    || paramValues[i] instanceof MultipartFile) {
                continue;
            }
            params.put(paramNames[i], paramValues[i]);
        }
        return truncate(String.valueOf(params), MAX_PARAM_LENGTH);
    }

    private String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    private void publishOperLog(OperLogRecord record) {
        log.info("OperLog: title={}, businessType={}, operName={}, operUrl={}, status={}, costTime={}ms",
                record.getTitle(), record.getBusinessType(), record.getOperName(),
                record.getOperUrl(), record.getStatus(), record.getCostTime());
        eventPublisher.publishEvent(new OperLogEvent(this, record));
    }
}
