package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.vo.TreeEnvVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界生命树环境 Controller（文档 2.2 / Sprint 2.2 情绪环境联动）。
 *
 * <p>公开接口：环境状态非用户敏感数据，未登录首页/世界树页亦需渲染；
 * 扫描触发接口见 {@link InternalTreeEnvController}（仅内部调用）。</p>
 */
@RestController
@RequestMapping("/tree-env")
@Tag(name = "生命树环境", description = "世界生命树情绪环境状态（四端环境渲染数据源）")
@RequiredArgsConstructor
public class TreeEnvController {

    private final TreeEnvService treeEnvService;

    @GetMapping
    @Operation(summary = "当前环境状态", description = "返回世界生命树当前环境"
            + "（SUNNY/RAIN/RAINBOW）、触发/过期时间与聚合情绪分数（10 分钟缓存，"
            + "可能为 null）。情绪由 mall-job 每 5 分钟扫描树洞 sentiment 聚合判定")
    @SentinelResource("WISH_TREE_ENV")
    public ApiResponse<TreeEnvVO> getCurrentEnv() {
        return ApiResponse.ok(treeEnvService.getCurrentEnv());
    }
}
