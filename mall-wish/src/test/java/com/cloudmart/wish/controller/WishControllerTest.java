package com.cloudmart.wish.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishDeleteResultVO;
import com.cloudmart.wish.vo.WishListItemVO;
import com.cloudmart.wish.vo.WishUpdateResultVO;
import com.cloudmart.wish.vo.WishVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("WishController 集成测试")
class WishControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final WishService wishService = Mockito.mock(WishService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WishController(wishService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /wishes - 发布心愿成功")
    void createWish_success() throws Exception {
        CreateWishRequest request = new CreateWishRequest(
                "考研上岸", "我要考上研究生", List.of("url1"),
                100L, List.of("学习"),
                WishVisibility.PUBLIC,
                LocalDateTime.now().plusMonths(6), false, false
        );
        WishCreateResultVO vo = new WishCreateResultVO(
                1L, "考研上岸",
                com.cloudmart.wish.enums.WishStatus.ACTIVE,
                com.cloudmart.wish.enums.FruitType.GLOW,
                LocalDateTime.now()
        );
        given(wishService.createWish(eq(1L), any(CreateWishRequest.class))).willReturn(vo);

        mockMvc.perform(post("/wishes")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("考研上岸"));
    }

    @Test
    @DisplayName("POST /wishes - 缺少 X-User-Id 头返回 401")
    void createWish_withoutUserId_returns401() throws Exception {
        CreateWishRequest request = new CreateWishRequest(
                "标题", "描述", null,
                100L, null,
                WishVisibility.PUBLIC,
                null, false, false
        );

        mockMvc.perform(post("/wishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /wishes - 标题为空返回校验失败 400")
    void createWish_blankTitle_returns400() throws Exception {
        CreateWishRequest request = new CreateWishRequest(
                "", "描述", null,
                100L, null,
                WishVisibility.PUBLIC,
                null, false, false
        );

        mockMvc.perform(post("/wishes")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /wishes - 列表查询成功返回 cursor 分页")
    void listWishes_success() throws Exception {
        WishListItemVO item = new WishListItemVO(
                1L, "标题", "描述", List.of(), 100L, "学习",
                List.of(), WishVisibility.PUBLIC,
                com.cloudmart.wish.enums.WishStatus.ACTIVE,
                com.cloudmart.wish.enums.FruitType.GLOW,
                1L, "作者", "avatar.png",
                0, 0, 0, 0, 0,
                null, LocalDateTime.now(), LocalDateTime.now()
        );
        given(wishService.listWishes(any())).willReturn(
                new WishService.WishListPage(List.of(item), "1", true)
        );

        mockMvc.perform(get("/wishes")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.nextCursor").value("1"))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
    }

    @Test
    @DisplayName("GET /wishes/{id} - 详情查询成功")
    void getWishDetail_success() throws Exception {
        WishVO vo = new WishVO(
                1L, "标题", "描述", List.of(), 100L, "学习",
                List.of(), WishVisibility.PUBLIC,
                com.cloudmart.wish.enums.WishStatus.ACTIVE,
                com.cloudmart.wish.enums.FruitType.GLOW,
                1L, "作者", "avatar.png",
                0, 0, 0, 0, 0,
                null, LocalDateTime.now(), LocalDateTime.now(),
                List.of(), 0, null
        );
        given(wishService.getWishDetail(eq(1L), any())).willReturn(vo);

        mockMvc.perform(get("/wishes/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("标题"));
    }

    @Test
    @DisplayName("PUT /wishes/{id} - 更新心愿成功")
    void updateWish_success() throws Exception {
        UpdateWishRequest request = new UpdateWishRequest(
                "新标题", null, null, null, null, null, null, null
        );
        WishUpdateResultVO vo = new WishUpdateResultVO(1L, LocalDateTime.now());
        given(wishService.updateWish(eq(1L), eq(1L), any(UpdateWishRequest.class))).willReturn(vo);

        mockMvc.perform(put("/wishes/1")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("DELETE /wishes/{id} - 软删心愿成功")
    void deleteWish_success() throws Exception {
        WishDeleteResultVO vo = new WishDeleteResultVO(1L, LocalDateTime.now());
        given(wishService.deleteWish(eq(1L), eq(1L))).willReturn(vo);

        mockMvc.perform(delete("/wishes/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /wishes/my - 已登录用户返回我的心愿列表")
    void listMyWishes_loggedIn_success() throws Exception {
        given(wishService.listMyWishes(eq(1L), any())).willReturn(
                new WishService.MyWishListPage(Collections.emptyList(), null, false)
        );

        mockMvc.perform(get("/wishes/my")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /wishes/my - 未登录用户返回空列表（不返回 401）")
    void listMyWishes_anonymous_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/wishes/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
