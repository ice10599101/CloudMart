package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.GroupActivityDTO;
import com.cloudmart.marketing.dto.GroupOrderDTO;
import com.cloudmart.marketing.dto.JoinGroupRequest;
import com.cloudmart.marketing.service.GroupActivityService;
import com.cloudmart.marketing.vo.GroupActivityVO;
import com.cloudmart.marketing.vo.GroupOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupActivityControllerTest {

    private MockMvc mockMvc;

    private final GroupActivityService groupActivityService = Mockito.mock(GroupActivityService.class);
    private final MarketingConverter marketingConverter = Mockito.mock(MarketingConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupActivityController(groupActivityService, marketingConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("获取拼团活动详情 - 成功返回信封格式")
    void getActivity_ShouldReturn200WithEnvelope() throws Exception {
        GroupActivityDTO dto = new GroupActivityDTO(1L, "三人团", "三人成团优惠",
                100L, 200L, new BigDecimal("199.00"), new BigDecimal("149.00"),
                3, 10, 2, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                "ENABLED", LocalDateTime.now());

        given(groupActivityService.getActivity(1L)).willReturn(dto);

        GroupActivityVO vo = new GroupActivityVO(1L, "三人团", "三人成团优惠",
                100L, 200L, new BigDecimal("199.00"), new BigDecimal("149.00"),
                3, 10, 2, "ENABLED", LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        given(marketingConverter.groupActivityDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/group/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("三人团"));
    }

    @Test
    @DisplayName("查询拼团活动列表 - 成功返回信封格式")
    void listActivities_ShouldReturn200WithEnvelope() throws Exception {
        GroupActivityDTO dto = new GroupActivityDTO(1L, "三人团", "三人成团优惠",
                100L, 200L, new BigDecimal("199.00"), new BigDecimal("149.00"),
                3, 10, 2, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                "ENABLED", LocalDateTime.now());

        IPage<GroupActivityDTO> dtoPage = new Page<>(1, 10, 1);
        dtoPage.setRecords(List.of(dto));
        given(groupActivityService.listActivities(null, 1, 10)).willReturn(dtoPage);

        GroupActivityVO vo = new GroupActivityVO(1L, "三人团", "三人成团优惠",
                100L, 200L, new BigDecimal("199.00"), new BigDecimal("149.00"),
                3, 10, 2, "ENABLED", LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        given(marketingConverter.groupActivityDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/group/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    @Test
    @DisplayName("加入拼团 - 成功返回信封格式")
    void joinGroup_ShouldReturn200WithEnvelope() throws Exception {
        GroupOrderDTO dto = new GroupOrderDTO(1L, 1L, 1L, 1, 3, "PENDING",
                LocalDateTime.now().plusDays(1), null, LocalDateTime.now(), List.of());

        given(groupActivityService.joinGroup(Mockito.eq(1L), Mockito.any(JoinGroupRequest.class))).willReturn(dto);

        GroupOrderVO vo = new GroupOrderVO(1L, 1L, 1L, 1, 3, "PENDING", LocalDateTime.now().plusDays(1));
        given(marketingConverter.groupOrderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/group/join")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("查询拼团组详情 - 成功返回信封格式")
    void getGroupOrder_ShouldReturn200WithEnvelope() throws Exception {
        GroupOrderDTO dto = new GroupOrderDTO(1L, 1L, 1L, 2, 3, "PENDING",
                LocalDateTime.now().plusDays(1), null, LocalDateTime.now(), List.of());

        given(groupActivityService.getGroupOrder(1L)).willReturn(dto);

        GroupOrderVO vo = new GroupOrderVO(1L, 1L, 1L, 2, 3, "PENDING", LocalDateTime.now().plusDays(1));
        given(marketingConverter.groupOrderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/group/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.currentNumber").value(2));
    }

    @Test
    @DisplayName("查询拼团组列表 - 成功返回信封格式")
    void listGroupOrders_ShouldReturn200WithEnvelope() throws Exception {
        GroupOrderDTO dto = new GroupOrderDTO(1L, 1L, 1L, 2, 3, "PENDING",
                LocalDateTime.now().plusDays(1), null, LocalDateTime.now(), List.of());

        IPage<GroupOrderDTO> dtoPage = new Page<>(1, 10, 1);
        dtoPage.setRecords(List.of(dto));
        given(groupActivityService.listGroupOrders(null, null, 1, 10)).willReturn(dtoPage);

        GroupOrderVO vo = new GroupOrderVO(1L, 1L, 1L, 2, 3, "PENDING", LocalDateTime.now().plusDays(1));
        given(marketingConverter.groupOrderDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/group/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("获取不存在的拼团活动 - 返回错误信封")
    void getActivity_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(groupActivityService.getActivity(999L))
                .willThrow(new BusinessException("GROUP_ACTIVITY_NOT_FOUND", "拼团活动不存在"));

        mockMvc.perform(get("/group/activities/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GROUP_ACTIVITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("拼团活动不存在"));
    }

    @Test
    @DisplayName("加入拼团 - 缺少X-User-Id返回401")
    void joinGroup_WhenMissingUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/group/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
