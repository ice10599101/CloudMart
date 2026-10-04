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
 * <p>转发至 mall-pet {@code /admin/**} 内部端点（SEC-01 后要求服务令牌 iss=mall-admin、
 * scope=pet:admin，由 {@link com.cloudmart.admin.config.PetServiceTokenConfig} 签发），
 * 权限点：{@code business:pet:list}（查询/看板）、{@code business:pet:edit}（配置与审核、
 * 举报处理、交易重试、成就补算）。配置类接口统一"带 id 为更新、不带 id 为新增"。
 * 外部路径统一 {@code /api/admin/pet/...}（类映射 /pet，方法不再重复 /pet 前缀）。</p>
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

    @org.springframework.web.bind.annotation.GetMapping("/wall/messages/export")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "留言导出 CSV（F6）", description = "from/to 均为 UTC 日期（含）；游标分页流式拼装，10 万行不超时")
    public void exportWallMessages(@org.springframework.web.bind.annotation.RequestParam(value = "from", required = false) String from,
                                   @org.springframework.web.bind.annotation.RequestParam(value = "to", required = false) String to,
                                   jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=pet-wall-messages.csv");
        java.io.Writer writer = new java.io.OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
        // UTF-8 BOM：Excel 直接打开不乱码
        writer.write('\ufeff');
        writer.write("id,petId,authorUserId,status,likeCount,content,createdAt\n");
        Long beforeId = null;
        int batchSize = 500;
        long total = 0;
        final long MAX_ROWS = 200_000;
        while (total <= MAX_ROWS) {
            ApiResponse<Object> page = petFeignClient.exportWallPage(beforeId, batchSize, from, to);
            if (page == null || !page.success() || !(page.data() instanceof java.util.List<?> rows) || rows.isEmpty()) {
                break;
            }
            for (Object rowObject : rows) {
                if (!(rowObject instanceof java.util.Map<?, ?> row)) {
                    continue;
                }
                writer.write(csvRow(row));
                total++;
                Object id = row.get("id");
                beforeId = id instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(id));
            }
            if (rows.size() < batchSize) {
                break;
            }
        }
        writer.flush();
    }

    /** 单行 CSV（引号转义；content 含逗号/换行时整体加引号） */
    private static String csvRow(java.util.Map<?, ?> row) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.get("id")).append(',')
                .append(row.get("petId")).append(',')
                .append(row.get("authorUserId")).append(',')
                .append(row.get("status")).append(',')
                .append(row.get("likeCount")).append(',');
        String content = String.valueOf(row.get("content"));
        if (content.contains(",") || content.contains("\"") || content.contains("\n") || content.contains("\r")) {
            // CSV 引号转义：内容内双引号翻倍
            sb.append('"').append(content.replace("\"", "\"\"")).append('"');
        } else {
            sb.append(content);
        }
        sb.append(',').append(row.get("createdAt")).append('\n');
        return sb.toString();
    }

    // ---------------- 数据看板 ----------------

    @GetMapping("/dashboard")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "宠物数据看板", description = "概览 + 近 N 日趋势 + 分布排行（days 默认 14）")
    public ApiResponse<Object> petDashboard(@RequestParam(value = "days", defaultValue = "14") Integer days) {
        return petFeignClient.petDashboard(days);
    }

    // ---- B14/B17/B21 新增管理能力代理（SEC-02：补细粒度权限与审计；操作者取认证上下文） ----
    // 说明：举报处理/重试/补算沿用 business:pet:edit；第 4.1 节细粒度权限码
    // （moderate/operation:retry/recalculate）随对应菜单迁移任务落地后替换。

    @org.springframework.web.bind.annotation.GetMapping("/reports")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "宠物举报列表", description = "status 过滤 + 分页")
    public ApiResponse<Object> petReports(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listPetReports(status, page, size);
    }

    // R05：旧 PUT /reports/{id}/handle 代理已删除——下游 mall-pet 旁路已停用，
    // 统一走 POST /reports/{id}/resolve（含处罚矩阵与审计）

    @org.springframework.web.bind.annotation.PostMapping("/reports/{id}/resolve")
    @OperLog(title = "宠物举报处理闭环", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "闭环处理举报", description = "action: CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED + reason 必填；处理后通知举报人")
    public ApiResponse<Void> resolvePetReport(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                              @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        return petFeignClient.resolvePetReport(id, body);
    }

    // ---------------- R05 处罚事实 / R04 相册审核（转发 mall-pet） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/sanctions")
    @Operation(summary = "处罚列表（R05）", description = "userId/status/scope 筛选；封禁范围、期限、理由与来源举报可追溯")
    @RequiresPermission("business:pet:edit")
    public ApiResponse<Object> listPetSanctions(
            @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(value = "scope", required = false) String scope,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listPetSanctions(userId, status, scope, page, size);
    }

    public record RevokeSanctionRequest(@jakarta.validation.constraints.NotBlank String reason) {
    }

    @org.springframework.web.bind.annotation.PostMapping("/sanctions/{id}/revoke")
    @OperLog(title = "宠物处罚撤销", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "撤销处罚（R05）", description = "理由必填留痕；撤销保留历史不物理删除；立即恢复对应写入能力")
    public ApiResponse<Void> revokePetSanction(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                               @org.springframework.web.bind.annotation.RequestBody RevokeSanctionRequest request) {
        return petFeignClient.revokePetSanction(id, java.util.Map.of("reason", request.reason()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/album/reviews")
    @Operation(summary = "相册审核队列（R04）", description = "PENDING 且 BOUND 的条目，按时间正序；auditStatus 可选过滤")
    @RequiresPermission("business:pet:edit")
    public ApiResponse<Object> petAlbumReviewQueue(
            @org.springframework.web.bind.annotation.RequestParam(value = "auditStatus", required = false) String auditStatus) {
        return petFeignClient.petAlbumReviewQueue(auditStatus);
    }

    public record AlbumRejectRequest(@jakarta.validation.constraints.NotBlank String reason) {
    }

    @org.springframework.web.bind.annotation.PostMapping("/album/{assetId}/reject")
    @OperLog(title = "宠物相册驳回", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "相册资源审核驳回（R04）", description = "理由必填留痕；被驳回条目保留在相册且不可公开")
    public ApiResponse<Object> rejectPetAlbum(@org.springframework.web.bind.annotation.PathVariable("assetId") Long assetId,
                                              @org.springframework.web.bind.annotation.RequestBody AlbumRejectRequest request) {
        return petFeignClient.rejectPetAlbum(assetId, java.util.Map.of("reason", request.reason()));
    }

    // ---------------- F2 赛季管理 ----------------

    @org.springframework.web.bind.annotation.GetMapping("/seasons")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "赛季列表")
    public ApiResponse<Object> listSeasons(@org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                           @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listSeasons(page, size);
    }

    @org.springframework.web.bind.annotation.PostMapping("/seasons")
    @OperLog(title = "宠物赛季配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新赛季", description = "ends_at 只允许延后；进行中赛季唯一")
    public ApiResponse<Object> upsertSeason(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> data) {
        return petFeignClient.upsertSeason(data);
    }

    @org.springframework.web.bind.annotation.GetMapping("/seasons/{id}/rewards")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "赛季奖励梯度列表")
    public ApiResponse<Object> listSeasonRewards(@org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return petFeignClient.listSeasonRewards(id);
    }

    @org.springframework.web.bind.annotation.PostMapping("/seasons/{id}/rewards")
    @OperLog(title = "宠物赛季奖励配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "保存奖励梯度", description = "整表替换；区间连续覆盖校验")
    public ApiResponse<Object> saveSeasonRewards(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                 @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> data) {
        return petFeignClient.saveSeasonRewards(id, data);
    }

    @org.springframework.web.bind.annotation.PostMapping("/seasons/{id}/settle")
    @OperLog(title = "宠物赛季手动结算", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "手动触发结算", description = "仅到期 ACTIVE 赛季；幂等")
    public ApiResponse<Void> settleSeason(@org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return petFeignClient.settleSeason(id);
    }

    // ---------------- F5 用户宠物查询与运营工具 ----------------

    @org.springframework.web.bind.annotation.GetMapping("/users/{userId}/pets")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "用户宠物全貌", description = "该用户全部宠物（状态/等级/属性/背包摘要/钱包余额）；客服工单查询用")
    public ApiResponse<Object> userPets(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId) {
        return petFeignClient.userPets(userId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/users/{userId}/pets/{petId}/adjust")
    @OperLog(title = "宠物数值调整", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "宠物数值调整", description = "白名单字段 + |delta|≤10000 + 理由必填；快照留痕可回溯")
    public ApiResponse<Object> adjustUserPet(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                             @org.springframework.web.bind.annotation.PathVariable("petId") Long petId,
                                             @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        return petFeignClient.adjustUserPet(userId, petId, body);
    }

    @org.springframework.web.bind.annotation.PostMapping("/users/{userId}/compensation")
    @OperLog(title = "宠物币补偿申请", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "钱包补偿申请", description = "走 W04 调账审批流（PENDING，须另一管理员审批入账）")
    public ApiResponse<Object> compensateUser(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                              @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        return petFeignClient.compensateUser(userId, body);
    }

    // ---------------- F1 食物配置 / F8 口头禅 ----------------

    @org.springframework.web.bind.annotation.GetMapping("/configs/foods")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "食物列表", description = "全量（含下架）；喂养效果服务端权威")
    public ApiResponse<Object> listFoods() {
        return petFeignClient.listFoods();
    }

    @org.springframework.web.bind.annotation.PostMapping("/configs/foods")
    @OperLog(title = "宠物食物配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新食物", description = "带 id 为更新；0≤hunger/happiness≤100")
    public ApiResponse<Object> upsertFood(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> data) {
        return petFeignClient.upsertFood(data);
    }

    @org.springframework.web.bind.annotation.PutMapping("/configs/foods/{id}/enabled")
    @OperLog(title = "宠物食物上下架", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "食物上下架")
    public ApiResponse<Object> toggleFood(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                          @org.springframework.web.bind.annotation.RequestParam("enabled") Boolean enabled) {
        return petFeignClient.toggleFood(id, enabled);
    }

    @org.springframework.web.bind.annotation.GetMapping("/configs/persona-phrases")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "口头禅列表", description = "按性格一行，DB 无行回落出厂默认")
    public ApiResponse<Object> listPersonaPhrases() {
        return petFeignClient.listPersonaPhrases();
    }

    @org.springframework.web.bind.annotation.PostMapping("/configs/persona-phrases")
    @OperLog(title = "宠物口头禅配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "保存口头禅", description = "按性格 upsert；60 秒内同步全部实例")
    public ApiResponse<Void> upsertPersonaPhrase(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> data) {
        return petFeignClient.upsertPersonaPhrase(data);
    }

    // ---------------- P0-1 内容安全：敏感词库 ----------------

    @org.springframework.web.bind.annotation.GetMapping("/configs/sensitive-words")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "敏感词列表", description = "status 过滤 + 分页（内容安全词库）")
    public ApiResponse<Object> listSensitiveWords(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) Integer status,
                                                  @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                                  @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listSensitiveWords(status, page, size);
    }

    @org.springframework.web.bind.annotation.PostMapping("/configs/sensitive-words")
    @OperLog(title = "宠物敏感词配置", businessType = 1)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "新增/更新敏感词", description = "category: POLITICS/ABUSE/AD/CRISIS；生效延迟 ≤1 分钟")
    public ApiResponse<Object> upsertSensitiveWord(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> data) {
        return petFeignClient.upsertSensitiveWord(data);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/configs/sensitive-words/{id}")
    @OperLog(title = "宠物敏感词删除", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "删除敏感词")
    public ApiResponse<Object> deleteSensitiveWord(@org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return petFeignClient.deleteSensitiveWord(id);
    }

    @org.springframework.web.bind.annotation.PostMapping("/achievements/recalculate")
    @OperLog(title = "宠物成就补算", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "成就补算", description = "按宠物扫描历史事实补漏成就；幂等，重复执行结果不变")
    public ApiResponse<Integer> recalculatePetAchievements(@org.springframework.web.bind.annotation.RequestParam("petId") Long petId) {
        return petFeignClient.recalculateAchievements(petId);
    }

    @org.springframework.web.bind.annotation.GetMapping("/operations")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "交易操作列表", description = "status/userId/petId 过滤 + 分页")
    public ApiResponse<Object> petOperations(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "petId", required = false) Long petId,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                             @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listPetOperations(status, userId, petId, page, size);
    }

    @org.springframework.web.bind.annotation.PostMapping("/operations/{operationId}/retry")
    @OperLog(title = "宠物交易操作重试", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "原单重试", description = "复用同一 operationId 幂等重试；已终态原样返回，不产生第二次资金变动")
    public ApiResponse<Void> retryPetOperation(@org.springframework.web.bind.annotation.PathVariable("operationId") String operationId) {
        return petFeignClient.retryPetOperation(operationId);
    }

    // ---------------- B21 配置治理 / BE-11 相册审核 ----------------
    // 说明：沿用现有权限码——治理查询为 business:pet:list，校验/回退/审核为 business:pet:edit；
    // 细粒度权限码随对应菜单迁移任务落地后替换。

    @org.springframework.web.bind.annotation.PostMapping("/config-governance/validate")
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "配置校验预览", description = "数值上下限组合校验，不落库（B21）")
    public ApiResponse<Void> validateConfigGovernance(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        return petFeignClient.validateConfigGovernance(body);
    }

    @org.springframework.web.bind.annotation.GetMapping("/config-governance/history")
    @RequiresPermission("business:pet:list")
    @Operation(summary = "配置历史版本", description = "按类型+配置 ID 查询发布/回退历史（最近 50 条）")
    public ApiResponse<Object> listConfigGovernanceHistory(@org.springframework.web.bind.annotation.RequestParam("configType") String configType,
                                                           @org.springframework.web.bind.annotation.RequestParam("configId") Long configId) {
        return petFeignClient.listConfigGovernanceHistory(configType, configId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/config-governance/rollback")
    @OperLog(title = "宠物配置回退", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "回退配置", description = "将指定版本快照写回目标行；回退动作本身留版本审计")
    public ApiResponse<Void> rollbackConfigGovernance(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        return petFeignClient.rollbackConfigGovernance(body);
    }

    @org.springframework.web.bind.annotation.PostMapping("/album/{assetId}/approve")
    @OperLog(title = "宠物相册审核", businessType = 2)
    @RequiresPermission("business:pet:edit")
    @Operation(summary = "相册资源审核通过", description = "BE-11：仅审核链路可设 APPROVED（用户上传进入时为 PENDING）")
    public ApiResponse<Object> approveAlbumAsset(@org.springframework.web.bind.annotation.PathVariable("assetId") Long assetId) {
        return petFeignClient.approveAlbumAsset(assetId);
    }
}
