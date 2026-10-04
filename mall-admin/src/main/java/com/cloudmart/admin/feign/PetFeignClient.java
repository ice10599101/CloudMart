package com.cloudmart.admin.feign;

import com.cloudmart.admin.config.PetServiceTokenConfig;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * mall-pet 管理端 Feign 客户端（宠物运营后台）。
 *
 * <p>下游端点在 mall-pet 内由 {@code @PreAuthorize("hasRole('INTERNAL')")} 保护，
 * SEC-01 后该角色只能由携带服务令牌（iss=mall-admin、scope=pet:admin）的请求建立；
 * {@link com.cloudmart.admin.config.PetServiceTokenConfig} 负责签发令牌，
 * {@code AdminFeignInterceptor} 另透传真实操作者（X-User-Id/X-Admin-Username）供审计。
 * 写接口统一用 {@code Map<String, Object>} 透传（后台表单字段与下游请求体一一对应）。</p>
 */
@FeignClient(contextId = "petFeignClient", name = "mall-pet", path = "/admin",
        configuration = PetServiceTokenConfig.class,
        fallbackFactory = PetFeignClientFallbackFactory.class)
public interface PetFeignClient {

    // ---------------- 既有配置：岗位 / 课程 ----------------

    @GetMapping("/configs/jobs")
    ApiResponse<Object> listJobs();

    @PostMapping("/configs/jobs")
    ApiResponse<Object> upsertJob(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/jobs/{id}/enabled")
    ApiResponse<Object> toggleJob(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/configs/studies")
    ApiResponse<Object> listStudies();

    @PostMapping("/configs/studies")
    ApiResponse<Object> upsertStudy(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/studies/{id}/enabled")
    ApiResponse<Object> toggleStudy(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    // ---------------- 二期配置：装备 / 皮肤 / 技能 / 进化 / 活动 ----------------

    @GetMapping("/configs/equipment")
    ApiResponse<Object> listEquipment();

    @PostMapping("/configs/equipment")
    ApiResponse<Object> upsertEquipment(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/equipment/{id}/enabled")
    ApiResponse<Object> toggleEquipment(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/configs/skins")
    ApiResponse<Object> listSkins();

    @PostMapping("/configs/skins")
    ApiResponse<Object> upsertSkin(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/skins/{id}/enabled")
    ApiResponse<Object> toggleSkin(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/configs/skills")
    ApiResponse<Object> listSkills();

    @PostMapping("/configs/skills")
    ApiResponse<Object> upsertSkill(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/skills/{id}/enabled")
    ApiResponse<Object> toggleSkill(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/configs/evolutions")
    ApiResponse<Object> listEvolutions();

    @PostMapping("/configs/evolutions")
    ApiResponse<Object> upsertEvolution(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/evolutions/{id}/enabled")
    ApiResponse<Object> toggleEvolution(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/configs/events")
    ApiResponse<Object> listEvents();

    @PostMapping("/configs/events")
    ApiResponse<Object> upsertEvent(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/events/{id}/enabled")
    ApiResponse<Object> toggleEvent(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @org.springframework.web.bind.annotation.GetMapping("/configs/events/{code}/occurrences")
    ApiResponse<Object> listEventOccurrences(@org.springframework.web.bind.annotation.PathVariable("code") String code);

    @org.springframework.web.bind.annotation.PostMapping("/configs/events/{code}/occurrences")
    ApiResponse<Object> publishEventOccurrence(@org.springframework.web.bind.annotation.PathVariable("code") String code,
                                               @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/configs/event-occurrences/{id}/close")
    ApiResponse<Object> closeEventOccurrence(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    @org.springframework.web.bind.annotation.GetMapping("/quests/receipts")
    ApiResponse<Object> listQuestReceipts(@org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "questCode", required = false) String questCode,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                          @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                          @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.PostMapping("/quests/receipts/{id}/replay")
    ApiResponse<Object> replayQuestReceipt(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    // ---------------- 三期配置：职业 / 家具 / 每日任务 ----------------

    @GetMapping("/pet/careers")
    ApiResponse<Object> listCareers();

    @PostMapping("/pet/careers")
    ApiResponse<Object> upsertCareer(@RequestBody Map<String, Object> data);

    @PutMapping("/pet/careers/{id}/enabled")
    ApiResponse<Object> toggleCareer(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/pet/furniture")
    ApiResponse<Object> listFurniture();

    @PostMapping("/pet/furniture")
    ApiResponse<Object> upsertFurniture(@RequestBody Map<String, Object> data);

    @PutMapping("/pet/furniture/{id}/enabled")
    ApiResponse<Object> toggleFurniture(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    @GetMapping("/pet/daily-quests")
    ApiResponse<Object> listDailyQuests();

    @PostMapping("/pet/daily-quests")
    ApiResponse<Object> upsertDailyQuest(@RequestBody Map<String, Object> data);

    @PutMapping("/pet/daily-quests/{id}/enabled")
    ApiResponse<Object> toggleDailyQuest(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled);

    // ---------------- 留言墙审核 ----------------

    @GetMapping("/pet/wall/messages")
    ApiResponse<Object> listWallMessages(@RequestParam(value = "petId", required = false) Long petId,
                                         @RequestParam(value = "authorUserId", required = false) Long authorUserId,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam("page") Integer page,
                                         @RequestParam("size") Integer size);

    @PutMapping("/pet/wall/messages/{id}/status")
    ApiResponse<Object> updateWallMessageStatus(@PathVariable("id") Long id,
                                                @RequestBody Map<String, Object> data);

    /** F6 导出分页：游标 + 时间范围，代理层流式拼装 CSV（mall-pet /admin/pet/wall/messages/export-page） */
    @org.springframework.web.bind.annotation.GetMapping("/pet/wall/messages/export-page")
    ApiResponse<Object> exportWallPage(@org.springframework.web.bind.annotation.RequestParam(value = "beforeId", required = false) Long beforeId,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "500") int size,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "from", required = false) String from,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "to", required = false) String to);

    // ---------------- 数据看板 ----------------

    @GetMapping("/pet/dashboard")
    ApiResponse<Object> petDashboard(@RequestParam("days") Integer days);

    // ---- B14/B17/B21 新增管理能力（举报处理 / 成就补算 / 交易操作查询与重试） ----

    @org.springframework.web.bind.annotation.GetMapping("/pet/reports")
    ApiResponse<Object> listPetReports(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size);


    @org.springframework.web.bind.annotation.PostMapping("/pet/reports/{id}/resolve")
    ApiResponse<Void> resolvePetReport(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                       @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    // ---------------- R05 处罚事实 / R04 相册审核（下游 mall-pet 新端点） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/pet/sanctions")
    ApiResponse<Object> listPetSanctions(@org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                         @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                         @org.springframework.web.bind.annotation.RequestParam(value = "scope", required = false) String scope,
                                         @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                         @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.PostMapping("/pet/sanctions/{id}/revoke")
    ApiResponse<Void> revokePetSanction(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                        @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.GetMapping("/pet/album/reviews")
    ApiResponse<Object> petAlbumReviewQueue(
            @org.springframework.web.bind.annotation.RequestParam(value = "auditStatus", required = false) String auditStatus);

    @org.springframework.web.bind.annotation.PostMapping("/pet/album/{assetId}/reject")
    ApiResponse<Object> rejectPetAlbum(@org.springframework.web.bind.annotation.PathVariable("assetId") Long assetId,
                                       @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    // ---------------- F1 食物配置 / F8 口头禅（下游 /admin/configs/**） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/configs/foods")
    ApiResponse<Object> listFoods();

    @org.springframework.web.bind.annotation.PostMapping("/configs/foods")
    ApiResponse<Object> upsertFood(@RequestBody Map<String, Object> data);

    @org.springframework.web.bind.annotation.PutMapping("/configs/foods/{id}/enabled")
    ApiResponse<Object> toggleFood(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                   @org.springframework.web.bind.annotation.RequestParam("enabled") Boolean enabled);

    @org.springframework.web.bind.annotation.GetMapping("/configs/persona-phrases")
    ApiResponse<Object> listPersonaPhrases();

    @org.springframework.web.bind.annotation.PostMapping("/configs/persona-phrases")
    ApiResponse<Void> upsertPersonaPhrase(@RequestBody Map<String, Object> data);

    // ---------------- P0-1 内容安全：敏感词库 ----------------

    @org.springframework.web.bind.annotation.GetMapping("/configs/sensitive-words")
    ApiResponse<Object> listSensitiveWords(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) Integer status,
                                           @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                           @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size);

    @org.springframework.web.bind.annotation.PostMapping("/configs/sensitive-words")
    ApiResponse<Object> upsertSensitiveWord(@RequestBody Map<String, Object> data);

    @org.springframework.web.bind.annotation.DeleteMapping("/configs/sensitive-words/{id}")
    ApiResponse<Object> deleteSensitiveWord(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    @org.springframework.web.bind.annotation.PostMapping("/pet/achievements/recalculate")
    ApiResponse<Integer> recalculateAchievements(@org.springframework.web.bind.annotation.RequestParam("petId") Long petId);

    @org.springframework.web.bind.annotation.GetMapping("/pet/operations")
    ApiResponse<Object> listPetOperations(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "petId", required = false) Long petId,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size);

    @org.springframework.web.bind.annotation.PostMapping("/pet/operations/{operationId}/retry")
    ApiResponse<Void> retryPetOperation(@org.springframework.web.bind.annotation.PathVariable("operationId") String operationId);

    // ---------------- F2 赛季（下游 /admin/seasons/**） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/seasons")
    ApiResponse<Object> listSeasons(@org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                    @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size);

    @org.springframework.web.bind.annotation.PostMapping("/seasons")
    ApiResponse<Object> upsertSeason(@RequestBody Map<String, Object> data);

    @org.springframework.web.bind.annotation.GetMapping("/seasons/{id}/rewards")
    ApiResponse<Object> listSeasonRewards(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    @org.springframework.web.bind.annotation.PostMapping("/seasons/{id}/rewards")
    ApiResponse<Object> saveSeasonRewards(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> data);

    @org.springframework.web.bind.annotation.PostMapping("/seasons/{id}/settle")
    ApiResponse<Void> settleSeason(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    // ---------------- F5 用户宠物查询与运营工具（下游 /admin/users/**） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/users/{userId}/pets")
    ApiResponse<Object> userPets(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId);

    @org.springframework.web.bind.annotation.PostMapping("/users/{userId}/pets/{petId}/adjust")
    ApiResponse<Object> adjustUserPet(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                      @org.springframework.web.bind.annotation.PathVariable("petId") Long petId,
                                      @RequestBody Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/users/{userId}/compensation")
    ApiResponse<Object> compensateUser(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                       @RequestBody Map<String, Object> body);

    // ---------------- W04 钱包管理（§8.4；下游 /admin/pet/wallet/**） ----------------

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/accounts")
    ApiResponse<Object> listWalletAccounts(@org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                           @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                           @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                           @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/accounts/{userId}")
    ApiResponse<Object> walletAccountOf(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/transactions")
    ApiResponse<Object> listWalletTransactions(@org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                               @org.springframework.web.bind.annotation.RequestParam(value = "bizType", required = false) String bizType,
                                               @org.springframework.web.bind.annotation.RequestParam(value = "direction", required = false) String direction,
                                               @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                               @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/accounts/{userId}/freeze")
    ApiResponse<Void> freezeWalletAccount(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                          @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/accounts/{userId}/unfreeze")
    ApiResponse<Void> unfreezeWalletAccount(@org.springframework.web.bind.annotation.PathVariable("userId") Long userId,
                                            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/adjustments")
    ApiResponse<Object> createWalletAdjustment(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/adjustments/{id}/approve")
    ApiResponse<Object> approveWalletAdjustment(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/adjustments/{id}/reject")
    ApiResponse<Object> rejectWalletAdjustment(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                               @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/adjustments")
    ApiResponse<Object> listWalletAdjustments(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                              @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
                                              @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                              @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/adjustments/{id}")
    ApiResponse<Object> walletAdjustmentOf(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/reconciliations")
    ApiResponse<Object> listWalletReconciliations(@org.springframework.web.bind.annotation.RequestParam("page") int page,
                                                  @org.springframework.web.bind.annotation.RequestParam("size") int size);

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/reconciliations/{runId}")
    ApiResponse<Object> walletReconciliationOf(@org.springframework.web.bind.annotation.PathVariable("runId") Long runId);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/reconciliations/run")
    ApiResponse<Void> triggerWalletReconcile();

    @org.springframework.web.bind.annotation.GetMapping("/pet/wallet/reconciliations/{runId}/diffs")
    ApiResponse<Object> walletReconciliationDiffs(@org.springframework.web.bind.annotation.PathVariable("runId") Long runId,
                                                  @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status);

    @org.springframework.web.bind.annotation.PostMapping("/pet/wallet/reconciliation-diffs/{id}/resolve")
    ApiResponse<Object> resolveWalletReconciliationDiff(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                        @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    // ---------------- BE-11 相册资源审核（下游 /admin/pet/album/**） ----------------

    @org.springframework.web.bind.annotation.PostMapping("/pet/album/{assetId}/approve")
    ApiResponse<Object> approveAlbumAsset(@org.springframework.web.bind.annotation.PathVariable("assetId") Long assetId);

    // ---------------- B21 配置治理（下游 /admin/pet/config-governance/**） ----------------

    @org.springframework.web.bind.annotation.PostMapping("/pet/config-governance/validate")
    ApiResponse<Void> validateConfigGovernance(@RequestBody Map<String, Object> body);

    @org.springframework.web.bind.annotation.GetMapping("/pet/config-governance/history")
    ApiResponse<Object> listConfigGovernanceHistory(@org.springframework.web.bind.annotation.RequestParam("configType") String configType,
                                                    @org.springframework.web.bind.annotation.RequestParam("configId") Long configId);

    @org.springframework.web.bind.annotation.PostMapping("/pet/config-governance/rollback")
    ApiResponse<Void> rollbackConfigGovernance(@RequestBody Map<String, Object> body);
}
