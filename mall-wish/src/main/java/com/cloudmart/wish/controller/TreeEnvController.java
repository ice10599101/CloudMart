package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.TreeEnvVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 世界生命树环境 Controller（文档 2.2 / Sprint 2.2 动态环境）。
 *
 * <p>公开接口：环境状态与渲染配置非用户敏感数据，未登录首页/世界树页
 * 亦需渲染；扫描/季节落库触发接口见 {@link InternalTreeEnvController}
 * （仅内部调用），管理端事件/配置操作见 AdminTreeEnvController。</p>
 */
@RestController
@RequestMapping("/tree-env")
@Tag(name = "生命树环境", description = "世界生命树动态环境状态与渲染配置（四端环境渲染数据源）")
@RequiredArgsConstructor
public class TreeEnvController {

    private final TreeEnvService treeEnvService;

    @GetMapping
    @Operation(summary = "当前环境状态（多维）", description = "返回世界生命树当前环境全量快照："
            + "情绪环境（SUNNY/RAIN/RAINBOW，树洞情绪联动）+ 季节（每日落库）+ 真实天气"
            + "（和风天气 5 分钟缓存，降级晴天）+ 时段（按 tzOffsetMinutes 计算，跨时区"
            + "用户按本地时区）+ 特殊事件（管理员触发全站同步）+ displayEnv（聚合展示"
            + "环境 code，优先级：特殊事件 > 情绪 RAINBOW/RAIN > 真实天气）。"
            + "moodScore 可能为 null（10 分钟缓存内无样本）")
    @SentinelResource("WISH_TREE_ENV")
    public ApiResponse<TreeEnvVO> getCurrentEnv(
            @Parameter(description = "客户端 UTC 时区偏移分钟（东八区=480；"
                    + "前端传 -new Date().getTimezoneOffset()；默认 0=UTC）", example = "480")
            @RequestParam(name = "tzOffsetMinutes", required = false) Integer tzOffsetMinutes) {
        return ApiResponse.ok(treeEnvService.getCurrentEnv(tzOffsetMinutes));
    }

    @GetMapping("/configs")
    @Operation(summary = "环境渲染配置（表化）", description = "返回已启用的环境配置列表"
            + "（priority 降序），含各环境四端渲染视觉参数 visual（crownColor/skyColor/"
            + "particle/lightCoreColor 等）。新增\"中秋\"等环境由管理端插入配置即可，"
            + "四端按 displayEnv/eventCode 取对应配置渲染，不改代码")
    @SentinelResource("WISH_TREE_ENV_CONFIGS")
    public ApiResponse<List<EnvConfigVO>> listActiveEnvConfigs() {
        return ApiResponse.ok(treeEnvService.listActiveEnvConfigs());
    }
}
