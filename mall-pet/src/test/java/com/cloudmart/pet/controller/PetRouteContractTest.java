package com.cloudmart.pet.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.service.impl.PetCompanionFeatureService;
import com.cloudmart.pet.service.impl.PetCooperationService;
import com.cloudmart.pet.service.impl.PetDigestService;
import com.cloudmart.pet.service.impl.PetCustodyCareService;
import com.cloudmart.pet.service.impl.PetMinigameService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R26/T10 路由契约测试（真实 Controller + GlobalExceptionHandler，无网关）：
 * 锁死 R03/R02/R11 修复后的路由形态——三端经网关（/api/pet/** StripPrefix=2）到达的
 * 服务内路径必须与 Controller 注册一致。R03 修复前本测试覆盖的路径全部 404。
 */
@DisplayName("宠物路由契约测试（R26/T10）")
class PetRouteContractTest {

    private MockMvc companionMockMvc;
    private MockMvc purchaseMockMvc;
    private MockMvc playMockMvc;
    private PetCompanionFeatureService companionService;
    private PetPurchaseApplicationService purchaseService;

    @BeforeEach
    void setUp() {
        companionService = mock(PetCompanionFeatureService.class);
        companionMockMvc = MockMvcBuilders.standaloneSetup(new PetCompanionFeatureController(companionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        purchaseService = mock(PetPurchaseApplicationService.class);
        purchaseMockMvc = MockMvcBuilders.standaloneSetup(new PetPurchaseController(purchaseService,
                        Mockito.mock(com.cloudmart.pet.repository.PetPurchaseOrderMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        playMockMvc = MockMvcBuilders.standaloneSetup(
                        new PetPlayFeatureController(mock(PetMinigameService.class),
                        mock(PetCustodyCareService.class),
                        mock(PetCooperationService.class),
                        mock(PetDigestService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        PetRequestContext.clear();
    }

    @Test
    @DisplayName("R03：GET /onboarding（服务内无 /pet 前缀）→ 200")
    void onboardingRouteWithoutPetPrefix() throws Exception {
        when(companionService.onboarding(100L)).thenReturn(Map.of("current", "FEED"));
        companionMockMvc.perform(get("/onboarding").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("R03：GET /pets/{petId}/diary（items 键 + size 参数生效）→ 200")
    void diaryRoute() throws Exception {
        Map<String, Object> diaryPage = new java.util.HashMap<>();
        diaryPage.put("items", List.of());
        diaryPage.put("nextCursor", null);
        diaryPage.put("hasMore", false);
        when(companionService.diary(eq(100L), eq(5L), any(), eq(20))).thenReturn(diaryPage);
        companionMockMvc.perform(get("/pets/5/diary")
                        .header("X-User-Id", "100")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("R03/T11：GET /pets/{petId}/memory-settings（服务端设置读取端点存在）→ 200")
    void memorySettingsRoute() throws Exception {
        when(companionService.memorySettings(100L, 5L))
                .thenReturn(Map.of("extract", false, "use", true));
        companionMockMvc.perform(get("/pets/5/memory-settings").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extract").value(false));
    }

    @Test
    @DisplayName("R03：GET /notify-settings（无 /pet 前缀）→ 200")
    void notifySettingsRoute() throws Exception {
        when(companionService.notifyPrefs(100L)).thenReturn(null);
        companionMockMvc.perform(get("/notify-settings").header("X-User-Id", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R11：GET /minigames/current（断线恢复端点存在）→ 200")
    void minigameCurrentRoute() throws Exception {
        // playService mock 返回默认 null 场景即可——路由契约只验证路径与参数绑定
        playMockMvc.perform(get("/minigames/current").header("X-User-Id", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R02：POST /purchases 无 Idempotency-Key → 400 PET_REQUEST_KEY_INVALID")
    void purchaseWithoutKeyRejected() throws Exception {
        when(purchaseService.purchase(any(), any(), any(), any(), Mockito.isNull(), any()))
                .thenThrow(new BusinessException("PET_REQUEST_KEY_INVALID", "缺少有效幂等键"));
        purchaseMockMvc.perform(post("/purchases")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId": 5, "itemType": "FOOD", "itemCode": "apple"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PET_REQUEST_KEY_INVALID"));
    }

    @Test
    @DisplayName("R02：POST /purchases 服务端转发认证上下文的幂等键")
    void purchasePassesContextKey() throws Exception {
        PetRequestContext.setIdempotencyKey("req-key-contract-0001");
        when(purchaseService.purchase(eq(100L), any(), eq("FOOD"), eq("apple"),
                eq("req-key-contract-0001"), any()))
                .thenReturn(new PetPurchaseApplicationService.PurchaseResult(
                        "2040", "pw_x", null, 80L, 5L, "FOOD", "apple", List.of("apple"), false, null));

        purchaseMockMvc.perform(post("/purchases")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId": 5, "itemType": "FOOD", "itemCode": "apple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("2040"));
    }

}
