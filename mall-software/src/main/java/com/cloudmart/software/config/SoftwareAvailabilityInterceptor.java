package com.cloudmart.software.config;

import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 模块总开关（software.enabled）拦截器（方案 11.2）：
 * 关闭后公共软件读写返回功能不可用；后台处置（/admin/**）与内部接口（/internal/**）
 * 不在本拦截器覆盖范围，作者仍可在受控编辑入口读取/保存草稿、撤回/下架/重新上架/删除。
 */
@Component
@RequiredArgsConstructor
public class SoftwareAvailabilityInterceptor implements HandlerInterceptor {

    /**
     * 关闭总开关时放行的受控入口（方法 + 路径精确匹配），其余 /software/** 一律拒绝。
     * 对应手册 11.2「作者仍可在受控编辑入口读取和保存草稿」及 4.0 矩阵保留操作。
     */
    private static final List<Rule> CONTROLLED_ENTRIES = List.of(
            new Rule("POST", Pattern.compile("^/software$")),
            new Rule("GET", Pattern.compile("^/software/mine$")),
            new Rule("GET", Pattern.compile("^/software/publisher-status$")),
            new Rule("GET", Pattern.compile("^/software/preferences$")),
            new Rule("PUT", Pattern.compile("^/software/preferences$")),
            new Rule("GET", Pattern.compile("^/software/\\d+/editor$")),
            new Rule("POST", Pattern.compile("^/software/\\d+/working-revision$")),
            new Rule("PUT", Pattern.compile("^/software/\\d+/revisions/\\d+$")),
            new Rule("POST",
                    Pattern.compile("^/software/\\d+/revisions/\\d+/(submit|withdraw)$")),
            new Rule("POST", Pattern.compile("^/software/\\d+/(unlist|relist)$")),
            new Rule("DELETE", Pattern.compile("^/software/\\d+$")));

    private final SoftwareProperties softwareProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (softwareProperties.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : CONTROLLED_ENTRIES) {
            if (rule.matches(request.getMethod(), path)) {
                return true;
            }
        }
        // 11.2：开关必须服务端生效，不能仅隐藏前端按钮
        throw new BusinessException("SOFTWARE_DISABLED", "软件模块暂不可用，请稍后再试");
    }

    private record Rule(String method, Pattern path) {
        boolean matches(String requestMethod, String requestPath) {
            return method.equalsIgnoreCase(requestMethod) && path.matcher(requestPath).matches();
        }
    }
}
