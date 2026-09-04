package com.cloudmart.wish.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wish.entity.UserAsset;
import com.cloudmart.wish.service.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工坊兑换 Controller 层测试。
 *
 * <p>回归背景：雪花 ID 经 JsSafeLongSerializer 以字符串下发，前端回传 string 形态的
 * assetId；旧实现 {@code (Number) body.get("assetId")} 强转直接 ClassCastException →
 * INTERNAL_ERROR。本测试锁定 string / number 两种形态均可正确反序列化。</p>
 */
@DisplayName("CollectionController 兑换参数反序列化")
class CollectionControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long SNOWFLAKE_ASSET_ID = 2095597250595123202L;

    private MockMvc mockMvc;
    private final CollectionService collectionService = Mockito.mock(CollectionService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CollectionController(collectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserAsset ownedAsset() {
        UserAsset ua = new UserAsset();
        ua.setUserId(1001L);
        ua.setAssetId(SNOWFLAKE_ASSET_ID);
        ua.setSource("EXCHANGE");
        ua.setStatus("OWNED");
        ua.setAcquiredAt(LocalDateTime.now());
        return ua;
    }

    @Test
    @DisplayName("assetId 为字符串（雪花 ID 超出 JS 安全范围的实际回传形态）→ 正常兑换")
    void exchange_stringAssetId() throws Exception {
        given(collectionService.exchange(eq(1001L), eq(SNOWFLAKE_ASSET_ID), eq("STARLIGHT")))
                .willReturn(ownedAsset());

        mockMvc.perform(post("/workshop/exchange")
                        .header(USER_ID_HEADER, 1001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"2095597250595123202\",\"paymentMethod\":\"STARLIGHT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(2095597250595123202L));
    }

    @Test
    @DisplayName("assetId 为数字（安全范围内 ID）→ 正常兑换")
    void exchange_numberAssetId() throws Exception {
        given(collectionService.exchange(eq(1001L), eq(123L), eq("STARLIGHT")))
                .willReturn(ownedAsset());

        mockMvc.perform(post("/workshop/exchange")
                        .header(USER_ID_HEADER, 1001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("assetId 缺失 → 400 参数校验错误（而非 500）")
    void exchange_missingAssetId_rejected() throws Exception {
        mockMvc.perform(post("/workshop/exchange")
                        .header(USER_ID_HEADER, 1001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
