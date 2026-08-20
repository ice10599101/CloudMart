package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.TreeFruitsQuery;
import com.cloudmart.wish.service.WorldTreeService;
import com.cloudmart.wish.vo.TreeFruitVO;
import com.cloudmart.wish.vo.WorldTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 世界生命树 3D 版 Controller（Sprint 2.1，文档 2.5 / 第二章 1.）。
 *
 * <p>公开接口：聚合状态与果实坐标非用户敏感数据，未登录首页/世界树页
 * 亦需渲染（与 /tree-env 同策略）；上树口径与公开列表一致，不泄露
 * PRIVATE/TREE_HOLE 心愿存在性。</p>
 */
@RestController
@RequestMapping("/tree")
@Tag(name = "世界生命树", description = "世界生命树 3D 场景数据源（聚合状态 + 果实视口分页）")
@RequiredArgsConstructor
public class WorldTreeController {

    private final WorldTreeService worldTreeService;

    @GetMapping
    @Operation(summary = "世界树聚合状态", description = "返回果实总数/绽放数/点亮总数"
            + "（Redis 缓存 TTL 5 分钟，防击穿 SETNX 短锁，Redis 异常 Fail-Open 直查 DB）、"
            + "当前环境（情绪联动实时读取）、季节（UTC 日期判定）与环境触发时间。"
            + "3D 场景首屏初始化数据源")
    @SentinelResource("WISH_TREE_AGGREGATION")
    public ApiResponse<WorldTreeVO> getTreeAggregation() {
        return ApiResponse.ok(worldTreeService.getTreeAggregation());
    }

    @GetMapping("/fruits")
    @Operation(summary = "果实分页（cursor + bounds 视口过滤）", description = "按 id DESC 游标分页，"
            + "游标为上一页最后一条果实 id；可选 bounds 视口过滤（弧度制：lat→phi [0,π]、"
            + "lng→theta [0,2π)，四参数需同时提供，异常整组忽略退化为全量分页不报错，"
            + "minLng > maxLng 表示跨 0/2π 环绕窗口）。3D 场景视角变化时动态加载视口内果实")
    @SentinelResource("WISH_TREE_FRUITS")
    public ApiResponse<List<TreeFruitVO>> listFruits(@Valid TreeFruitsQuery query) {
        WorldTreeService.FruitPage page = worldTreeService.listFruits(query);
        return ApiResponse.okWithCursor(page.records(), query.pageSize(),
                page.nextCursor(), page.hasMore());
    }
}
