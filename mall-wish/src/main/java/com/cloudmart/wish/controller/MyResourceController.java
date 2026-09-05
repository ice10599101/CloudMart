package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.MyResourcesVO;
import com.cloudmart.wish.vo.ResourceLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人星光资源 Controller（文档 L848）。
 *
 * <p>余额概览 + 流水分页；四端「星光余额展示」统一数据源（文档 L1910/L1915/L1916）。</p>
 */
@RestController
@RequestMapping("/my/resources")
@Tag(name = "我的星光", description = "余额概览 + 流水查询（个人数据，需登录）")
@RequiredArgsConstructor
public class MyResourceController {

    private final UserStatService userStatService;

    @GetMapping
    @Operation(summary = "星光余额概览", description = "当前余额 + 今日已获取/已消耗；"
            + "今日边界与流水 createdAt 写入时区一致")
    @SentinelResource("WISH_MY_RESOURCES")
    public ApiResponse<MyResourcesVO> getMyResources(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(userStatService.getMyResources(userId));
    }

    @GetMapping("/logs")
    @Operation(summary = "星光流水", description = "时间倒序 cursor 分页（游标为上一页末条 id）；"
            + "type 可选 EARN/SPEND 过滤；amount 恒为正数，方向由 type 表达")
    @SentinelResource("WISH_MY_RESOURCE_LOGS")
    public ApiResponse<List<ResourceLogVO>> listResourceLogs(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "类型过滤：EARN / SPEND，缺省全部")
            @RequestParam(required = false) ResourceLogType type,
            @Parameter(description = "游标（上一页末条流水 ID，缺省第一页）")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "页大小，默认 20，上限 50")
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(userStatService.listResourceLogs(userId, type, cursor, pageSize));
    }
}
