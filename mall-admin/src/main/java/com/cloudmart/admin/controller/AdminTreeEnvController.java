package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.TreeEnvFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 生命树环境管理代理接口（Sprint 2.2）。
 *
 * <p>转发至 mall-wish /admin/tree-env/** 内部端点；触发特殊事件的
 * 管理员身份经 AdminFeignInterceptor 以 X-User-Id 头透传。权限点
 * {@code business:treeEnv:*} 在管理后台角色界面配置。</p>
 */
@RestController
@Tag(name = "生命树环境管理", description = "特殊事件触发台 + 环境配置管理（表化，新增环境不改代码）")
@RequiredArgsConstructor
public class AdminTreeEnvController {

    private final TreeEnvFeignClient treeEnvFeignClient;

    // ========== 特殊事件触发台 ==========

    @PostMapping("/wish/tree-env/special-events")
    @OperLog(title = "特殊事件触发", businessType = 1)
    @RequiresPermission("business:treeEnv:trigger")
    @Operation(summary = "触发全站特殊事件", description = "如流星雨/极光，全站同步展示；"
            + "单活跃事件语义（自动结束当前活跃事件）；eventCode 须为已启用的 SPECIAL_EVENT 配置")
    public ApiResponse<Object> triggerSpecialEvent(@RequestBody Map<String, Object> data) {
        return treeEnvFeignClient.triggerSpecialEvent(data);
    }

    @PutMapping("/wish/tree-env/special-events/{id}/end")
    @OperLog(title = "特殊事件结束", businessType = 2)
    @RequiresPermission("business:treeEnv:trigger")
    @Operation(summary = "手动结束特殊事件", description = "幂等：已结束直接返回；"
            + "结束后全站恢复常规环境展示")
    public ApiResponse<Object> endSpecialEvent(@PathVariable Long id) {
        return treeEnvFeignClient.endSpecialEvent(id);
    }

    @GetMapping("/wish/tree-env/special-events")
    @RequiresPermission("business:treeEnv:list")
    @Operation(summary = "特殊事件历史列表", description = "triggeredAt 倒序（触发台历史记录）")
    public ApiResponse<Object> listSpecialEvents(@RequestParam(defaultValue = "50") int limit) {
        return treeEnvFeignClient.listSpecialEvents(limit);
    }

    // ========== 环境配置管理（表化） ==========

    @GetMapping("/wish/tree-env/configs")
    @RequiresPermission("business:treeEnv:list")
    @Operation(summary = "环境配置全量列表", description = "含下架配置（管理端需查看与操作全部），"
            + "priority 降序；visual 为渲染参数 JSON 供编辑器回显")
    public ApiResponse<Object> listEnvConfigs() {
        return treeEnvFeignClient.listEnvConfigs();
    }

    @PostMapping("/wish/tree-env/configs")
    @OperLog(title = "环境配置管理", businessType = 1)
    @RequiresPermission("business:treeEnv:add")
    @Operation(summary = "新增环境配置", description = "表化核心入口：新增\"中秋\"等环境仅插入"
            + "配置行，四端渲染不改代码；envCode 唯一")
    public ApiResponse<Object> createEnvConfig(@RequestBody Map<String, Object> data) {
        return treeEnvFeignClient.createEnvConfig(data);
    }

    @PutMapping("/wish/tree-env/configs/{id}")
    @OperLog(title = "环境配置管理", businessType = 2)
    @RequiresPermission("business:treeEnv:edit")
    @Operation(summary = "编辑环境配置", description = "envCode 不可修改（天气/季节/事件链路关联键）")
    public ApiResponse<Object> updateEnvConfig(@PathVariable Long id,
                                               @RequestBody Map<String, Object> data) {
        return treeEnvFeignClient.updateEnvConfig(id, data);
    }

    @PutMapping("/wish/tree-env/configs/{id}/status")
    @OperLog(title = "环境配置上下架", businessType = 2)
    @RequiresPermission("business:treeEnv:edit")
    @Operation(summary = "上/下架环境配置", description = "下架后不出现在公开配置列表，"
            + "特殊事件触发校验失败（无启用配置）")
    public ApiResponse<Object> updateEnvConfigStatus(@PathVariable Long id,
                                                     @RequestBody Map<String, Boolean> data) {
        return treeEnvFeignClient.updateEnvConfigStatus(id, (Map<String, Object>) (Map<?, ?>) data);
    }
}
