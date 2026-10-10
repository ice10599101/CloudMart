package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.BuyFurnitureRequest;
import com.cloudmart.pet.dto.PlaceFurnitureRequest;
import com.cloudmart.pet.dto.UpdateRoomSettingsRequest;
import com.cloudmart.pet.dto.UpdateRoomThemeRequest;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetVisitService;
import com.cloudmart.pet.vo.PetHomeVO;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetRoomLikeVO;
import com.cloudmart.pet.vo.PetRoomVisitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物家园接口（三期）。
 *
 * <p>家园 = 房间 + 家具 + 来访：{@code /home} 是自己的家园，
 * {@code /home/{petId}} 是访问他人家园（每日次数上限，同一房间每日只给一次奖励）。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物家园", description = "家园面板/家具购买摆放/主题/设置/来访/点赞")
@RequiredArgsConstructor
public class PetHomeController {

    private final PetHomeService homeService;
    private final PetVisitService visitService;

    @GetMapping("/home/visitors/today")
    @Operation(summary = "今日访客", description = "访客日志（§6）：今日来访我家的访客列表（拜访事实，含宠物名/主人昵称/来源）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<java.util.List<com.cloudmart.pet.vo.VisitorLogVO>> todayVisitors(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(visitService.todayVisitors(userId));
    }

    @GetMapping("/home")
    @Operation(summary = "我的家园", description = "房间状态 + 已摆放 + 背包家具 + 家园商城 + 舒适度加成（每日首次进入送礼）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetHomeVO> home(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(homeService.home(userId));
    }

    @PostMapping("/home/furniture/buy")
    @Operation(summary = "购买家具", description = "先入包再扣星光（失败回滚）；重复购买 409 PET_ITEM_ALREADY_OWNED")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetInventoryItemVO> buyFurniture(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody BuyFurnitureRequest request) {
        return ApiResponse.ok(homeService.buyFurniture(userId, request));
    }

    @PostMapping("/home/furniture/place")
    @Operation(summary = "摆放家具", description = "越界 400 PET_ROOM_POS_INVALID；格子占用 409 PET_ROOM_POS_OCCUPIED；未拥有 409")
    @SentinelResource("PET_HOME")
    public ApiResponse<PetHomeVO> place(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody PlaceFurnitureRequest request) {
        return ApiResponse.ok(homeService.place(userId, request));
    }

    @DeleteMapping("/home/furniture")
    @Operation(summary = "卸下家具", description = "按格子卸下，舒适度同步重算")
    @SentinelResource("PET_HOME")
    public ApiResponse<PetHomeVO> removeFurniture(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam("posX") Integer posX,
            @RequestParam("posY") Integer posY) {
        return ApiResponse.ok(homeService.remove(userId, posX, posY));
    }

    @PutMapping("/home/theme")
    @Operation(summary = "更换墙纸/地板", description = "必须已拥有且分类匹配（墙纸 WALL / 地板 FLOOR）；传 null 恢复默认")
    @SentinelResource("PET_HOME")
    public ApiResponse<PetHomeVO> updateTheme(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody UpdateRoomThemeRequest request) {
        return ApiResponse.ok(homeService.updateTheme(userId, request));
    }

    @PutMapping("/home/settings")
    @Operation(summary = "家园设置", description = "来访开关 + 欢迎语（最多 40 字）")
    @SentinelResource("PET_HOME")
    public ApiResponse<PetHomeVO> updateSettings(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody UpdateRoomSettingsRequest request) {
        return ApiResponse.ok(homeService.updateSettings(userId, request));
    }

    /**
     * PET-19：读取与命令分离——GET 只读预览（不记拜访事实、不发奖励、不扣额度），
     * 页面刷新/预加载/重复 GET 零业务副作用；访问命令由 POST /home/{petId}/visits 显式提交。
     */
    @GetMapping("/home/{petId}")
    @Operation(summary = "预览他人家园（PET-19 只读）", description = "未公开 403 PET_ROOM_PRIVATE；"
            + "不产生拜访事实与奖励，返回 visitedToday 供前端判断是否需要显式提交拜访")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetRoomVisitVO> preview(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(homeService.previewHome(userId, petId));
    }

    @PostMapping("/home/{petId}/visits")
    @Operation(summary = "拜访他人家园（PET-19 命令入口）", description = "未公开 403 PET_ROOM_PRIVATE；"
            + "每日次数上限；同一房间每日只给一次奖励；重复同日拜访 409 PET_VISIT_COOLDOWN")
    @SentinelResource("PET_VISIT")
    public ApiResponse<PetRoomVisitVO> visit(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(homeService.visit(userId, petId));
    }

    @PostMapping("/home/{petId}/like")
    @Operation(summary = "给家园点赞", description = "uk 幂等（同一房间每人一次）；每日点赞上限")
    @SentinelResource("PET_VISIT")
    public ApiResponse<PetRoomLikeVO> like(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(homeService.like(userId, petId));
    }
}
