package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.PetFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 社区宠物运营后台代理接口。
 *
 * <p>转发至 mall-pet {@code /admin/**} 内部端点（由网关 AdminAuthGlobalFilter 校验管理员身份），
 * 权限点：{@code business:pet:list}（查询/看板）、{@code business:pet:edit}（配置与审核）。
 * 配置类接口统一"带 id 为更新、不带 id 为新增"。</p>
 */
@RestController
@RequestMapping("/pet")
@Tag(name = "宠物运营", description = "宠物配置管理、留言审核与数据看板代理接口")
@RequiredArgsConstructor
public class AdminPetController {

    private final PetFeignClient petFeignClient;

    // ---------------- 既有配置：岗位 / 课程 ----------------

    @GetMapping("/configs/jobs")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "打工岗位列表", description = "全量（含停用）")
    public ApiResponse<Object> listJobs() {
        return petFeignClient.listJobs();
    }

    @PostMapping("/configs/jobs")
    @OperLog(title = "宠物岗位配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新岗位", description = "带 id 为更新；数值服务端权威")
    public ApiResponse<Object> upsertJob(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertJob(data);
    }

    @PutMapping("/configs/jobs/{id}/enabled")
    @OperLog(title = "宠物岗位启停", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "岗位启停")
    public ApiResponse<Object> toggleJob(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleJob(id, enabled);
    }

    @GetMapping("/configs/studies")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "读书课程列表")
    public ApiResponse<Object> listStudies() {
        return petFeignClient.listStudies();
    }

    @PostMapping("/configs/studies")
    @OperLog(title = "宠物课程配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新课程")
    public ApiResponse<Object> upsertStudy(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertStudy(data);
    }

    @PutMapping("/configs/studies/{id}/enabled")
    @OperLog(title = "宠物课程启停", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "课程启停")
    public ApiResponse<Object> toggleStudy(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleStudy(id, enabled);
    }

    // ---------------- 二期配置 ----------------

    @GetMapping("/configs/equipment")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "装备列表")
    public ApiResponse<Object> listEquipment() {
        return petFeignClient.listEquipment();
    }

    @PostMapping("/configs/equipment")
    @OperLog(title = "宠物装备配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新装备")
    public ApiResponse<Object> upsertEquipment(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertEquipment(data);
    }

    @PutMapping("/configs/equipment/{id}/enabled")
    @OperLog(title = "宠物装备上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "装备上下架")
    public ApiResponse<Object> toggleEquipment(@PathVariable("id") Long id,
                                              @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleEquipment(id, enabled);
    }

    @GetMapping("/configs/skins")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "皮肤列表")
    public ApiResponse<Object> listSkins() {
        return petFeignClient.listSkins();
    }

    @PostMapping("/configs/skins")
    @OperLog(title = "宠物皮肤配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新皮肤")
    public ApiResponse<Object> upsertSkin(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertSkin(data);
    }

    @PutMapping("/configs/skins/{id}/enabled")
    @OperLog(title = "宠物皮肤上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "皮肤上下架")
    public ApiResponse<Object> toggleSkin(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleSkin(id, enabled);
    }

    @GetMapping("/configs/skills")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "技能列表")
    public ApiResponse<Object> listSkills() {
        return petFeignClient.listSkills();
    }

    @PostMapping("/configs/skills")
    @OperLog(title = "宠物技能配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新技能")
    public ApiResponse<Object> upsertSkill(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertSkill(data);
    }

    @PutMapping("/configs/skills/{id}/enabled")
    @OperLog(title = "宠物技能上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "技能上下架")
    public ApiResponse<Object> toggleSkill(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleSkill(id, enabled);
    }

    @GetMapping("/configs/evolutions")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "进化链列表")
    public ApiResponse<Object> listEvolutions() {
        return petFeignClient.listEvolutions();
    }

    @PostMapping("/configs/evolutions")
    @OperLog(title = "宠物进化配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新进化")
    public ApiResponse<Object> upsertEvolution(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertEvolution(data);
    }

    @PutMapping("/configs/evolutions/{id}/enabled")
    @OperLog(title = "宠物进化启停", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "进化启停")
    public ApiResponse<Object> toggleEvolution(@PathVariable("id") Long id,
                                               @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleEvolution(id, enabled);
    }

    @GetMapping("/configs/events")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "社区宠物活动列表")
    public ApiResponse<Object> listEvents() {
        return petFeignClient.listEvents();
    }

    @PostMapping("/configs/events")
    @OperLog(title = "社区宠物活动配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新活动")
    public ApiResponse<Object> upsertEvent(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertEvent(data);
    }

    @PutMapping("/configs/events/{id}/enabled")
    @OperLog(title = "社区宠物活动上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "活动上下架")
    public ApiResponse<Object> toggleEvent(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleEvent(id, enabled);
    }

    // ---------------- 三期配置：职业 / 家具 / 每日任务 ----------------

    @GetMapping("/careers")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "职业列表", description = "全量（含停招）")
    public ApiResponse<Object> listCareers() {
        return petFeignClient.listCareers();
    }

    @PostMapping("/careers")
    @OperLog(title = "宠物职业配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新职业", description = "promoteToCode 需指向同路线下一阶")
    public ApiResponse<Object> upsertCareer(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertCareer(data);
    }

    @PutMapping("/careers/{id}/enabled")
    @OperLog(title = "宠物职业启停", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "职业开放/停止招聘")
    public ApiResponse<Object> toggleCareer(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleCareer(id, enabled);
    }

    @GetMapping("/furniture")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "家具列表")
    public ApiResponse<Object> listFurniture() {
        return petFeignClient.listFurniture();
    }

    @PostMapping("/furniture")
    @OperLog(title = "宠物家具配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新家具", description = "comfort 决定家园舒适度")
    public ApiResponse<Object> upsertFurniture(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertFurniture(data);
    }

    @PutMapping("/furniture/{id}/enabled")
    @OperLog(title = "宠物家具上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "家具上下架")
    public ApiResponse<Object> toggleFurniture(@PathVariable("id") Long id,
                                              @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleFurniture(id, enabled);
    }

    @GetMapping("/daily-quests")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "每日任务列表")
    public ApiResponse<Object> listDailyQuests() {
        return petFeignClient.listDailyQuests();
    }

    @PostMapping("/daily-quests")
    @OperLog(title = "宠物每日任务配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新每日任务", description = "questType 必须是已埋点口径")
    public ApiResponse<Object> upsertDailyQuest(@RequestBody Map<String, Object> data) {
        return petFeignClient.upsertDailyQuest(data);
    }

    @PutMapping("/daily-quests/{id}/enabled")
    @OperLog(title = "宠物每日任务启停", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "每日任务启停")
    public ApiResponse<Object> toggleDailyQuest(@PathVariable("id") Long id,
                                                @RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleDailyQuest(id, enabled);
    }

    // ---------------- 留言墙审核 ----------------

    @GetMapping("/wall/messages")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "留言列表", description = "含 HIDDEN/DELETED 全量（审核溯源）；按宠物/作者/状态筛选")
    public ApiResponse<Object> listWallMessages(@RequestParam(value = "petId", required = false) Long petId,
                                                @RequestParam(value = "authorUserId", required = false) Long authorUserId,
                                                @RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return petFeignClient.listWallMessages(petId, authorUserId, status, page, size);
    }

    @PutMapping("/wall/messages/{id}/status")
    @OperLog(title = "宠物留言审核", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "留言隐藏/恢复/删除", description = "NORMAL 恢复 / HIDDEN 隐藏 / DELETED 删除")
    public ApiResponse<Object> updateWallMessageStatus(@PathVariable("id") Long id,
                                                       @RequestBody Map<String, Object> data) {
        return petFeignClient.updateWallMessageStatus(id, data);
    }

    // ---------------- 数据看板 ----------------

    @GetMapping("/dashboard")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "宠物数据看板", description = "概览 + 近 N 日趋势 + 分布排行（days 默认 14）")
    public ApiResponse<Object> petDashboard(@RequestParam(value = "days", defaultValue = "14") Integer days) {
        return petFeignClient.petDashboard(days);
    }

    // ---- B14/B17/B21 新增管理能力代理 ----

    @org.springframework.web.bind.annotation.GetMapping("/pet/reports")
    public ApiResponse<Object> petReports(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listPetReports(status, page, size);
    }

    @org.springframework.web.bind.annotation.PutMapping("/pet/reports/{id}/handle")
    public ApiResponse<Void> handlePetReport(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                             @org.springframework.web.bind.annotation.RequestParam("action") String action,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "adminUserId", required = false) Long adminUserId) {
        return petFeignClient.handlePetReport(id, action, adminUserId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/pet/achievements/recalculate")
    public ApiResponse<Integer> recalculatePetAchievements(@org.springframework.web.bind.annotation.RequestParam("petId") Long petId) {
        return petFeignClient.recalculateAchievements(petId);
    }

    @org.springframework.web.bind.annotation.GetMapping("/pet/operations")
    public ApiResponse<Object> petOperations(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "petId", required = false) Long petId,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listPetOperations(status, userId, petId, page, size);
    }

    @org.springframework.web.bind.annotation.PostMapping("/pet/operations/{operationId}/retry")
    public ApiResponse<Void> retryPetOperation(@org.springframework.web.bind.annotation.PathVariable("operationId") String operationId) {
        return petFeignClient.retryPetOperation(operationId);
    }
}
