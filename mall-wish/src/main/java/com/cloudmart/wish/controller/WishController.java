package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.MyWishListQuery;
import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.dto.WishListQuery;
import com.cloudmart.wish.service.FulfillmentService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.InheritResultVO;
import com.cloudmart.wish.vo.WishFulfillmentSubmitVO;
import com.cloudmart.wish.vo.WishFulfillmentVO;
import com.cloudmart.wish.vo.MyWishListItemVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishDeleteResultVO;
import com.cloudmart.wish.vo.WishListItemVO;
import com.cloudmart.wish.vo.WishSparkVO;
import com.cloudmart.wish.vo.WishUpdateResultVO;
import com.cloudmart.wish.vo.WishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 心愿核心 Controller（对应文档 2.1 节）。
 *
 * <p>路由前缀 /wishes，网关层 /api/wish/** → 转发到本服务 /wishes/**。</p>
 */
@RestController
@RequestMapping("/wishes")
@Tag(name = "心愿核心", description = "心愿发布、查询、编辑、删除接口")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;
    private final FulfillmentService fulfillmentService;

    @PostMapping
    @Operation(summary = "发布心愿", description = "用户发布新心愿，初始状态 ACTIVE，果实类型 GLOW")
    @SentinelResource("WISH_CREATE")
    public ApiResponse<WishCreateResultVO> createWish(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "创建心愿请求") @Valid @RequestBody CreateWishRequest request) {
        WishCreateResultVO vo = wishService.createWish(userId, request);
        return ApiResponse.ok(vo);
    }

    @GetMapping
    @Operation(summary = "心愿列表（cursor 分页）", description = "公开心愿列表，按 created_at 倒序，游标为 id")
    @SentinelResource("WISH_QUERY")
    public ApiResponse<List<WishListItemVO>> listWishes(@Valid WishListQuery query) {
        WishService.WishListPage page = wishService.listWishes(query);
        return ApiResponse.okWithCursor(page.records(), query.pageSize(),
                page.nextCursor(), page.hasMore());
    }

    @GetMapping("/{id}")
    @Operation(summary = "心愿详情", description = "获取心愿详情，含最近成长记录与进度信息")
    @SentinelResource("WISH_DETAIL")
    public ApiResponse<WishVO> getWishDetail(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "当前用户 ID（网关注入，可空）")
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        WishVO vo = wishService.getWishDetail(wishId, userId);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新心愿", description = "仅作者可更新，FULFILLED 状态不可设置 SPARK 果实")
    public ApiResponse<WishUpdateResultVO> updateWish(
            @Parameter(description = "当前用户 ID", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "更新请求") @Valid @RequestBody UpdateWishRequest request) {
        WishUpdateResultVO vo = wishService.updateWish(userId, wishId, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除心愿（软删）", description = "仅作者可删除，软删后列表不展示但 DB 保留")
    public ApiResponse<WishDeleteResultVO> deleteWish(
            @Parameter(description = "当前用户 ID", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        WishDeleteResultVO vo = wishService.deleteWish(userId, wishId);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{id}/spark")
    @Operation(summary = "设为星火永久收藏", description = "仅作者可对已还愿（FULFILLED）心愿操作，"
            + "fruit_type: BLOOM → SPARK，status 保持不变；幂等（已是 SPARK 直接成功）。"
            + "SPARK 心愿在世界生命树永久展示，可被他人收藏到个人收藏馆。"
            + "errors: 403 WISH_NOT_AUTHOR / 404 WISH_NOT_FOUND / 409 WISH_NOT_FULFILLED / WISH_SPARK_CONFLICT")
    @SentinelResource("WISH_SPARK")
    public ApiResponse<WishSparkVO> sparkWish(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        WishSparkVO vo = wishService.sparkWish(userId, wishId);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/my")
    @Operation(summary = "我的心愿列表（cursor 分页）", description = "当前用户的心愿列表，含进度百分比。未登录返回空列表。")
    public ApiResponse<List<MyWishListItemVO>> listMyWishes(
            @Parameter(description = "当前用户 ID（网关注入，可空）")
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Valid MyWishListQuery query) {
        if (userId == null) {
            return ApiResponse.okWithCursor(Collections.emptyList(), query.pageSize(), null, false);
        }
        WishService.MyWishListPage page = wishService.listMyWishes(userId, query);
        return ApiResponse.okWithCursor(page.records(), query.pageSize(),
                page.nextCursor(), page.hasMore());
    }

    @PostMapping("/{id}/fulfillment")
    @Operation(summary = "提交还愿", description = "仅作者可对 ACTIVE/OVERDUE 心愿提交还愿故事，"
            + "提交即流转 FULFILLED + 果实 BLOOM，先发后审（audit_status=PENDING），奖励星光 +50")
    @SentinelResource("WISH_FULFILL")
    public ApiResponse<WishFulfillmentSubmitVO> submitFulfillment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "还愿请求") @Valid @RequestBody SubmitFulfillmentRequest request) {
        WishFulfillmentSubmitVO vo = fulfillmentService.submitFulfillment(userId, wishId, request);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/{id}/fulfillment")
    @Operation(summary = "还愿详情", description = "获取心愿的还愿故事（含照片/感悟）。"
            + "公开心愿的还愿匿名可见；PRIVATE/TREE_HOLE 心愿仅作者可见；未还愿返回 404")
    @SentinelResource("WISH_FULFILLMENT_DETAIL")
    public ApiResponse<WishFulfillmentVO> getFulfillmentDetail(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "当前用户 ID（网关注入，可空）")
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        WishFulfillmentVO vo = fulfillmentService.getFulfillmentDetail(wishId, userId);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{id}/fulfillment/inherit")
    @Operation(summary = "传承推送", description = "作者对已实现心愿定向推送曾同求用户，"
            + "通知含还愿故事摘要（\"你的同愿实现了\"）；一次还愿仅一次传承。"
            + "errors: 403 WISH_NOT_AUTHOR / 409 WISH_NOT_FULFILLED / WISH_ALREADY_INHERITED")
    @SentinelResource("WISH_FULFILLMENT_INHERIT")
    public ApiResponse<InheritResultVO> inheritFulfillment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @RequestBody(required = false) InheritRequest request) {
        String message = request == null ? null : request.message();
        return ApiResponse.ok(fulfillmentService.inheritFulfillment(userId, wishId, message));
    }

    @DeleteMapping("/{id}/fulfillment")
    @Operation(summary = "撤回还愿故事", description = "作者软删还愿故事（心愿保持 FULFILLED，"
            + "历史事实不回退）；community 传承帖子同步隐藏")
    @SentinelResource("WISH_FULFILLMENT_WITHDRAW")
    public ApiResponse<Void> withdrawFulfillment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        fulfillmentService.withdrawFulfillment(userId, wishId);
        return ApiResponse.ok(null);
    }

    // ---------------- Sprint 1.3 打卡与成长记录（补齐） ----------------

    @PostMapping("/{id}/checkin")
    @Operation(summary = "打卡", description = "每日一次（uk_checkin_daily 幂等）；更新连续打卡；"
            + "发放星光 +2（CHECKIN 流水）。errors: 403 WISH_NOT_AUTHOR / 409 WISH_ALREADY_CHECKIN_TODAY")
    @SentinelResource("WISH_CHECKIN")
    public ApiResponse<WishService.CheckinResultVO> checkin(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String content = body != null ? body.get("content") : null;
        String mood = body != null ? body.get("mood") : null;
        return ApiResponse.ok(wishService.checkinWish(userId, wishId, content, mood));
    }

    @PostMapping("/{id}/growth")
    @Operation(summary = "添加成长记录", description = "TEXT/IMAGE/VIDEO/DIARY 四类型；"
            + "可选 progressDelta 驱动进度更新（乐观锁 version 防并发覆盖）")
    @SentinelResource("WISH_GROWTH_ADD")
    public ApiResponse<WishService.GrowthRecordVO> addGrowthRecord(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @RequestBody WishService.AddGrowthRequest request) {
        return ApiResponse.ok(wishService.addGrowthRecord(userId, wishId, request));
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "心愿进度", description = "当前值/目标值/百分比/乐观锁版本号")
    @SentinelResource("WISH_PROGRESS")
    public ApiResponse<WishService.ProgressDetail> getProgress(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        return ApiResponse.ok(wishService.getWishProgress(wishId));
    }

    @PutMapping("/{id}/progress")
    @Operation(summary = "进度乐观锁更新", description = "作者专用；version 不符 → 409 WISH_VERSION_CONFLICT（含最新进度）")
    @SentinelResource("WISH_PROGRESS_UPDATE")
    public ApiResponse<WishService.ProgressDetail> updateProgress(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @RequestBody WishService.ProgressUpdateRequest request) {
        return ApiResponse.ok(wishService.updateProgress(userId, wishId, request));
    }

    @GetMapping("/{id}/checkins")
    @Operation(summary = "心愿打卡日历", description = "作者专用；month=YYYY-MM，返回当月已打卡日期数组")
    public ApiResponse<WishService.CheckinCalendarVO> checkinCalendar(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "月份 YYYY-MM（必填）") @RequestParam String month) {
        return ApiResponse.ok(wishService.getCheckinCalendar(userId, wishId, month));
    }

    @PutMapping("/{id}/growth-records/{recordId}")
    @Operation(summary = "编辑成长记录", description = "作者专用；content/mediaUrls 可选更新")
    public ApiResponse<WishService.GrowthRecordVO> updateGrowthRecord(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "记录 ID", required = true) @PathVariable Long recordId,
            @RequestBody Map<String, Object> body) {
        final String content = (String) body.get("content");
        @SuppressWarnings("unchecked")
        final List<String> mediaUrls = (List<String>) body.get("mediaUrls");
        return ApiResponse.ok(wishService.updateGrowthRecord(userId, wishId, recordId, content, mediaUrls));
    }

    @DeleteMapping("/{id}/growth-records/{recordId}")
    @Operation(summary = "删除成长记录", description = "作者专用；进度为历史事实不回退")
    public ApiResponse<Void> deleteGrowthRecord(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "记录 ID", required = true) @PathVariable Long recordId) {
        wishService.deleteGrowthRecord(userId, wishId, recordId);
        return ApiResponse.ok(null);
    }

    /** 传承发起请求。 */
    public record InheritRequest(String message) {
    }
}
