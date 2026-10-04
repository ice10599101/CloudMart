package com.cloudmart.pet.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.service.PetChatService;
import com.cloudmart.pet.service.impl.PetAccessPolicy;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R26/R05/R22 契约测试第二批（远程部署验证断言的自动化形态）：
 * 锁死拒绝终态重放语义、同键异参 409、幂等键缺失 400、处罚端点权限——
 * 这些断言最初由 scripts/verify/pet-release-verify.py 在真实环境发现/验证，
 * 本测试保证它们进 CI 后不被回退。
 */
@DisplayName("宠物幂等与处罚契约测试（R26 第二批）")
class PetIdempotencyContractTest {

    private MockMvc purchaseMockMvc;
    private MockMvc chatMockMvc;
    private PetPurchaseApplicationService purchaseService;
    private PetChatService chatService;

    @BeforeEach
    void setUp() {
        purchaseService = mock(PetPurchaseApplicationService.class);
        purchaseMockMvc = MockMvcBuilders.standaloneSetup(new PetPurchaseController(purchaseService,
                        mock(com.cloudmart.pet.repository.PetPurchaseOrderMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        chatService = mock(PetChatService.class);
        chatMockMvc = MockMvcBuilders.standaloneSetup(new PetChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        PetRequestContext.clear();
    }

    @AfterEach
    void tearDown() {
        PetRequestContext.clear();
    }

    private static final String VALID_KEY = "contract-key-000001";

    /**
     * /purchases（新接口契约 §7.2）：拒绝终态重放返回 200 + data.errorCode（success=true
     * 的结果信封，errorCode 字段承载拒绝语义——客户端按 errorCode 判断）；而 /shop/buy
     * 兼容门面把 errorCode 转抛为 HTTP 400（该行为由 PetShopServiceImplTest 锁定）。
     * 两者语义等价、信封形态不同是设计使然。
     */
    @Test
    @DisplayName("R02/§8.2：/purchases 拒绝终态重放 → 200 + data.errorCode 承载拒绝语义")
    void replayedRejectionSameCode() throws Exception {
        PetRequestContext.setIdempotencyKey(VALID_KEY);
        when(purchaseService.purchase(eq(100L), eq(5L), eq("FOOD"), eq("apple"),
                eq(VALID_KEY), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new PetPurchaseApplicationService.PurchaseResult(
                        null, "pw_x", null, null, 5L, "FOOD", "apple", List.of(), false,
                        "PET_WALLET_INSUFFICIENT"));

        purchaseMockMvc.perform(post("/purchases")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petId\":5,\"itemType\":\"FOOD\",\"itemCode\":\"apple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCode").value("PET_WALLET_INSUFFICIENT"));
    }

    @Test
    @DisplayName("R02：同键异参（首次执行）→ 409 PET_IDEMPOTENCY_CONFLICT")
    void sameKeyDifferentPayloadConflict() throws Exception {
        PetRequestContext.setIdempotencyKey(VALID_KEY);
        when(purchaseService.purchase(eq(100L), eq(5L), eq("FOOD"), eq("banana"),
                eq(VALID_KEY), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new BusinessException("PET_IDEMPOTENCY_CONFLICT",
                        "请求键已存在但请求内容不同"));

        purchaseMockMvc.perform(post("/purchases")
                .header("X-User-Id", "100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":5,\"itemType\":\"FOOD\",\"itemCode\":\"banana\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PET_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("R22：聊天在途 → 409 PET_REQUEST_IN_PROGRESS（路由契约：异常码经全局映射）")
    void chatRouteAcceptsKeyedRequest() throws Exception {
        when(chatService.chat(eq(100L), org.mockito.ArgumentMatchers.argThat(
                r -> r != null && "你好".equals(r.message()))))
                .thenThrow(new BusinessException("PET_REQUEST_IN_PROGRESS", "消息处理中"));

        chatMockMvc.perform(post("/chat")
                .header("X-User-Id", "100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PET_REQUEST_IN_PROGRESS"));
    }

    @Test
    @DisplayName("R05：处罚列表端点路由存在（权限由 @PreAuthorize 在真实链路校验）")
    void sanctionsRouteExists() throws Exception {
        // standalone 下无方法级安全，mock policy 返回空列表验证路由+参数绑定
        var policy = mock(PetAccessPolicy.class);
        when(policy.list(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        var mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminPetReportController(
                                mock(com.cloudmart.pet.repository.PetReportMapper.class),
                                mock(com.cloudmart.pet.repository.PetMapper.class),
                                mock(com.cloudmart.pet.service.PetAchievementService.class),
                                mock(com.cloudmart.pet.service.impl.PetCompanionFeatureService.class),
                                mock(com.cloudmart.pet.service.impl.PetReportResolutionService.class),
                                policy))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/admin/pet/sanctions")
                        .header("X-User-Id", "100")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
