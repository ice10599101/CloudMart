package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetMinigameRound;
import com.cloudmart.pet.service.impl.PetPlayFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 新增玩法接口（N04 接球小游戏 / N05 有限托管 / N06 好友合作周任务 / N07 收藏图鉴）。
 */
@RestController
@Tag(name = "宠物新增玩法", description = "接球小游戏、离线托管、合作周任务、收藏图鉴")
@RequiredArgsConstructor
public class PetPlayFeatureController {

    private final PetPlayFeatureService playService;

    // ---------------- N04 ----------------

    @PostMapping("/pet/pets/{petId}/minigames")
    @Operation(summary = "开始接球局（N04）", description = "服务端下发规则版本/开始截止时间/随机序列；"
            + "每日 5 局有收益并消耗玩耍额度；超限转训练局（rewardEligible=false，不动属性）")
    public ApiResponse<Map<String, Object>> startRound(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "宠物 ID") @PathVariable("petId") Long petId) {
        return ApiResponse.ok(playService.startRound(userId));
    }

    public record RoundOpsRequest(List<Map<String, Object>> ops) {
    }

    @PostMapping("/pet/minigames/{roundId}/ops")
    @Operation(summary = "提交操作批次（N04）", description = "服务端校验机会编号/目标/接收时间窗口；乱序/重放/超期不计分")
    public ApiResponse<Map<String, Object>> submitOps(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("roundId") Long roundId,
            @RequestBody RoundOpsRequest request) {
        return ApiResponse.ok(playService.submitOps(userId, roundId, request.ops()));
    }

    @PostMapping("/pet/minigames/{roundId}/settle")
    @Operation(summary = "结束结算（N04）", description = "截止前返回进行中；CAS 幂等——同一局重复结束只发一次奖励；"
            + "断网重试返回同一结果")
    public ApiResponse<Map<String, Object>> settleRound(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("roundId") Long roundId) {
        return ApiResponse.ok(playService.settle(userId, roundId));
    }

    @GetMapping("/pet/minigames")
    @Operation(summary = "对局历史（N04）")
    public ApiResponse<List<PetMinigameRound>> minigameHistory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(playService.history(userId, page, size));
    }

    // ---------------- N05 ----------------

    @PostMapping("/pet/custody/start")
    @Operation(summary = "启动托管（N05）", description = "每自然周 1 次免费、最长 24h、每次一只；期间禁止工作/读书/捞瓶/有收益对战玩耍")
    public ApiResponse<Map<String, Object>> startCustody(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.startCustody(userId));
    }

    @GetMapping("/pet/custody")
    @Operation(summary = "托管状态（N05）", description = "惰性应用照顾（饱食<30→50 最多2次；清洁<30→50 最多1次）；不产出养成收益")
    public ApiResponse<Map<String, Object>> custodyStatus(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.custodyStatus(userId));
    }

    @GetMapping("/pet/offline-digest")
    @Operation(summary = "离线摘要（N05）", description = "按上次确认游标聚合离线变化/待领取/来访/里程碑；查询不重发奖励")
    public ApiResponse<Map<String, Object>> offlineDigest(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.offlineDigest(userId));
    }

    @PostMapping("/pet/offline-digest/confirm")
    @Operation(summary = "确认离线摘要（N05）", description = "只推进查看游标，幂等；不删除真实事件")
    public ApiResponse<Map<String, Object>> confirmOfflineDigest(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.confirmOfflineDigest(userId));
    }

    @PostMapping("/pet/cooperation/{cooperationId}/claim")
    @Operation(summary = "领取合作奖励（N06）", description = "COMPLETED 后参与双方各自领取装饰；已拥有转替代星光 20 走 B01；幂等")
    public ApiResponse<Map<String, Object>> claimCooperation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("cooperationId") Long cooperationId) {
        return ApiResponse.ok(playService.claimCooperationReward(userId, cooperationId));
    }

    @PostMapping("/pet/custody/end")
    @Operation(summary = "提前结束托管（N05）", description = "不退还本周次数；恢复普通自然变化")
    public ApiResponse<Void> endCustody(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        playService.endCustody(userId);
        return ApiResponse.ok(null);
    }

    // ---------------- N06 ----------------

    @PostMapping("/pet/cooperation")
    @Operation(summary = "创建合作邀请（N06）", description = "每自然周一支；本周剩余不足 3 个业务日拒绝并提示下周可参加")
    public ApiResponse<Map<String, Object>> createCooperation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.createCooperation(userId));
    }

    @PostMapping("/pet/cooperation/{cooperationId}/accept")
    @Operation(summary = "接受邀请（N06）", description = "重验周名额/屏蔽/剩余业务日；双方各自绑定当周宠物")
    public ApiResponse<Map<String, Object>> acceptCooperation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("cooperationId") Long cooperationId) {
        return ApiResponse.ok(playService.acceptCooperation(userId, cooperationId, null));
    }

    @GetMapping("/pet/cooperation")
    @Operation(summary = "我的合作任务（N06）", description = "当前+历史；对方隐私最小化（仅贡献次数与宠物摘要）")
    public ApiResponse<List<PetCooperation>> cooperations(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.cooperations(userId));
    }

    @PostMapping("/pet/cooperation/{cooperationId}/leave")
    @Operation(summary = "退出合作（N06）", description = "停止新增贡献、保留历史；未达成不发奖；名额不恢复")
    public ApiResponse<Void> leaveCooperation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("cooperationId") Long cooperationId) {
        playService.leaveCooperation(userId, cooperationId);
        return ApiResponse.ok(null);
    }

    // ---------------- N07 ----------------

    @GetMapping("/pet/collection")
    @Operation(summary = "收藏图鉴（N07）", description = "按用户累计、多宠共享；未解锁返回线索；隐藏彩蛋不泄漏正文")
    public ApiResponse<List<Map<String, Object>>> collection(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(playService.collection(userId, category, page, size));
    }

    @GetMapping("/pet/collection/stats")
    @Operation(summary = "收藏统计与进度（N07）")
    public ApiResponse<Map<String, Object>> collectionStats(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(playService.collectionStats(userId));
    }
}
