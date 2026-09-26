package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
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
 * 仅接受 AdminFeignInterceptor 注入 X-Internal-Call 头的内部调用；
 * 写接口统一用 {@code Map<String, Object>} 透传（后台表单字段与下游请求体一一对应）。</p>
 */
@FeignClient(contextId = "petFeignClient", name = "mall-pet", path = "/admin",
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

    // ---------------- 数据看板 ----------------

    @GetMapping("/pet/dashboard")
    ApiResponse<Object> petDashboard(@RequestParam("days") Integer days);

    // ---- B14/B17/B21 新增管理能力（举报处理 / 成就补算 / 交易操作查询与重试） ----

    @org.springframework.web.bind.annotation.GetMapping("/pet/reports")
    ApiResponse<Object> listPetReports(@org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
                                       @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size);

    @org.springframework.web.bind.annotation.PutMapping("/pet/reports/{id}/handle")
    ApiResponse<Void> handlePetReport(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                      @org.springframework.web.bind.annotation.RequestParam("action") String action,
                                      @org.springframework.web.bind.annotation.RequestParam(value = "adminUserId", required = false) Long adminUserId);

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
}
