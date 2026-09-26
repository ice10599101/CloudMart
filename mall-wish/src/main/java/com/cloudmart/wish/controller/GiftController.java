package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.SendGiftRequest;
import com.cloudmart.wish.service.GiftService;
import com.cloudmart.wish.vo.GiftRecordPageVO;
import com.cloudmart.wish.vo.GiftRecordVO;
import com.cloudmart.wish.vo.GiftSummaryVO;
import com.cloudmart.wish.vo.GiftVO;
import com.cloudmart.wish.vo.SendGiftResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全站虚拟礼物 Controller（用户端）。
 *
 * <p>错误码：404 GIFT_NOT_FOUND / GIFT_TARGET_NOT_FOUND、409 GIFT_OFF_SHELF、
 * 402 WISH_STARLIGHT_INSUFFICIENT、429 WISH_RATE_LIMITED、400 GIFT_TARGET_TYPE_INVALID。</p>
 */
@RestController
@RequestMapping("/gifts")
@Tag(name = "礼物", description = "全站虚拟礼物：目录/送礼/送礼记录（心愿、帖子、直播间）")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @GetMapping
    @Operation(summary = "礼物目录", description = "上架礼物列表（sort 升序），用于礼物选择器展示")
    @SentinelResource("GIFT_CATALOG")
    public ApiResponse<List<GiftVO>> listGifts() {
        return ApiResponse.ok(giftService.listOnShelfGifts());
    }

    @PostMapping("/send")
    @Operation(summary = "送礼物", description = "用星光余额送礼（心愿/帖子/直播间）；余额不足返回 402；"
            + "重复提交请携带 X-Idempotency-Key 请求头")
    @SentinelResource("GIFT_SEND")
    @Idempotent(prefix = "wish-gift", ttl = 10)
    public ApiResponse<SendGiftResultVO> sendGift(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "幂等键（一次送礼动作一个键，超时重试沿用同键）")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "送礼请求") @Valid @RequestBody SendGiftRequest request) {
        return ApiResponse.ok(giftService.sendGift(userId, request, idempotencyKey));
    }

    @GetMapping("/my/summary")
    @Operation(summary = "我的礼物资产总览", description = "送/收两方向累计件数与星光；"
            + "礼物为即时消费（送礼即扣星光），无库存语义；当前星光余额见 /wish/my/resources")
    public ApiResponse<GiftSummaryVO> mySummary(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(giftService.getMyGiftSummary(userId));
    }

    @GetMapping("/records/sent")
    @Operation(summary = "我送出的礼物", description = "id 倒序 cursor 分页（游标为上一页末条 id）")
    public ApiResponse<List<GiftRecordVO>> listSentRecords(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "游标（上一页末条记录 ID）")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "页大小（默认 20，上限 50）")
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        GiftRecordPageVO page = giftService.listSentRecords(userId, cursor, pageSize);
        return ApiResponse.okWithCursor(page.records(), page.pageSize(),
                page.nextCursor(), Boolean.TRUE.equals(page.hasMore()));
    }

    @GetMapping("/records/received")
    @Operation(summary = "我收到的礼物", description = "id 倒序 cursor 分页")
    public ApiResponse<List<GiftRecordVO>> listReceivedRecords(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "游标（上一页末条记录 ID）")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "页大小（默认 20，上限 50）")
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        GiftRecordPageVO page = giftService.listReceivedRecords(userId, cursor, pageSize);
        return ApiResponse.okWithCursor(page.records(), page.pageSize(),
                page.nextCursor(), Boolean.TRUE.equals(page.hasMore()));
    }

    @GetMapping("/targets/{targetType}/{targetId}")
    @Operation(summary = "场景礼物墙", description = "某心愿/帖子/直播间的最新送礼记录（id 倒序 cursor 分页）")
    @SentinelResource("GIFT_TARGET_RECORDS")
    public ApiResponse<List<GiftRecordVO>> listTargetRecords(
            @Parameter(description = "当前用户 ID（网关注入；匿名缺省）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long viewerId,
            @Parameter(description = "送礼场景", required = true) @PathVariable("targetType") String targetType,
            @Parameter(description = "场景对象 ID", required = true) @PathVariable("targetId") Long targetId,
            @Parameter(description = "游标（上一页末条记录 ID）")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "页大小（默认 20，上限 50）")
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        GiftRecordPageVO page = giftService.listTargetRecords(viewerId, targetType, targetId, cursor, pageSize);
        return ApiResponse.okWithCursor(page.records(), page.pageSize(),
                page.nextCursor(), Boolean.TRUE.equals(page.hasMore()));
    }
}
