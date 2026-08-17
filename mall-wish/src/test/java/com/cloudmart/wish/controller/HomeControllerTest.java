package com.cloudmart.wish.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wish.service.HomeService;
import com.cloudmart.wish.vo.HomeAggregationVO;
import com.cloudmart.wish.vo.HomeEntriesVO;
import com.cloudmart.wish.vo.HotResonanceItemVO;
import com.cloudmart.wish.vo.MyWishSummaryVO;
import com.cloudmart.wish.vo.TodayRecommendItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("HomeController 集成测试")
class HomeControllerTest {

    private MockMvc mockMvc;

    private final HomeService homeService = Mockito.mock(HomeService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(homeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /home - 已登录用户返回聚合数据")
    void getHomeAggregation_loggedIn_success() throws Exception {
        HomeAggregationVO vo = buildHomeAggregation();
        given(homeService.getHomeAggregation(any())).willReturn(vo);

        mockMvc.perform(get("/home")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entries.wishEntry").value(true))
                .andExpect(jsonPath("$.data.entries.mapEntry").value(false))
                .andExpect(jsonPath("$.data.todayRecommend[0].title").value("推荐心愿"));
    }

    @Test
    @DisplayName("GET /home - 未登录用户也能访问")
    void getHomeAggregation_anonymous_success() throws Exception {
        HomeAggregationVO vo = new HomeAggregationVO(
                null, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), new HomeEntriesVO(true, false, false)
        );
        given(homeService.getHomeAggregation(any())).willReturn(vo);

        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entries.wishEntry").value(true));
    }

    private HomeAggregationVO buildHomeAggregation() {
        TodayRecommendItemVO todayItem = new TodayRecommendItemVO(
                1L, "推荐心愿", "cover.png", "用户A", 50,
                com.cloudmart.wish.enums.FruitType.GLOW
        );
        MyWishSummaryVO myWish = new MyWishSummaryVO(
                10L, "我的心愿",
                com.cloudmart.wish.enums.WishStatus.ACTIVE, 50,
                com.cloudmart.wish.enums.FruitType.GLOW
        );
        HotResonanceItemVO hotItem = new HotResonanceItemVO(2L, "热门心愿", 100);
        return new HomeAggregationVO(
                null,
                List.of(todayItem),
                List.of(myWish),
                List.of(hotItem),
                new HomeEntriesVO(true, false, false)
        );
    }
}
