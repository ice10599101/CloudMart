package com.cloudmart.admin.controller;

import com.cloudmart.common.annotation.RequiresPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R31：社区管理列表读权限契约回归。
 *
 * <p>V21 给社区 8 个列表端点补齐了 {@code community:*:list} 权限点，与 admin_menu
 * C 行 perms 对齐。本测试用反射锁定"路径 → 权限标识"映射，防止后续重构误删注解
 * 重新退回"登录即可读全部举报/私聊审核数据"的状态。</p>
 */
@DisplayName("AdminCommunityController 列表权限契约")
class AdminCommunityListPermissionContractTest {

    private static final Map<String, String> LIST_PATH_TO_PERMISSION = Map.of(
            "/community/posts", "community:post:list",
            "/community/comments", "community:comment:list",
            "/community/tags", "community:tag:list",
            "/community/reports", "community:report:list",
            "/community/badges", "community:badge:list",
            "/community/growth/level-configs", "community:growth:list",
            "/review/pending/posts", "community:review:list",
            "/review/sensitive-words", "community:review:list");

    @Test
    @DisplayName("8 个社区 GET 列表端点全部声明对应的 community:*:list 权限")
    void listEndpoints_RequireListPermissions() {
        for (Map.Entry<String, String> expectation : LIST_PATH_TO_PERMISSION.entrySet()) {
            Method handler = findGetHandler(expectation.getKey());

            RequiresPermission annotation = handler.getAnnotation(RequiresPermission.class);
            assertThat(annotation)
                    .as("GET %s 必须声明 @RequiresPermission", expectation.getKey())
                    .isNotNull();
            assertThat(annotation.value())
                    .as("GET %s 权限标识", expectation.getKey())
                    .isEqualTo(expectation.getValue());
        }
    }

    private Method findGetHandler(String path) {
        for (Method method : AdminCommunityController.class.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping != null && path.equals(mapping.value()[0])) {
                return method;
            }
        }
        throw new IllegalStateException("未找到 GET " + path + " 处理方法");
    }
}
