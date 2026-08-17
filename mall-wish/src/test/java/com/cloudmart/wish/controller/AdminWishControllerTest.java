package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.service.AdminWishService;
import com.cloudmart.wish.vo.AdminWishVO;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminWishController 集成测试")
class AdminWishControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final AdminWishService adminWishService = Mockito.mock(AdminWishService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminWishController(adminWishService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /admin/wishes - 分页列表查询成功")
    void listWishes_success() throws Exception {
        AdminWishVO vo = buildAdminWishVO();
        Page<AdminWishVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(vo));
        given(adminWishService.listWishes(any(AdminWishListQuery.class))).willReturn(page);

        mockMvc.perform(get("/admin/wishes")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /admin/wishes/{id} - 详情查询成功")
    void getWishDetail_success() throws Exception {
        AdminWishVO vo = buildAdminWishVO();
        given(adminWishService.getWishDetail(eq(1L))).willReturn(vo);

        mockMvc.perform(get("/admin/wishes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /admin/wishes/{id}/audit - 审核通过成功")
    void auditWish_approve_success() throws Exception {
        AdminAuditWishRequest request = new AdminAuditWishRequest(AuditStatus.APPROVED, null);
        AdminWishVO vo = buildAdminWishVO();
        vo = new AdminWishVO(
                vo.id(), vo.userId(), vo.title(), vo.description(), vo.mediaUrls(),
                vo.categoryId(), vo.categoryName(), vo.tags(), vo.visibility(),
                vo.status(), vo.fruitType(),
                AuditStatus.APPROVED, vo.auditStrategy(), true,
                vo.lightCount(), vo.sameWishCount(), vo.blessCount(), vo.supportCount(),
                vo.expectedAt(), vo.fulfilledAt(), vo.createdAt(), vo.updatedAt(), vo.deletedAt()
        );
        given(adminWishService.auditWish(eq(1L), any(AdminAuditWishRequest.class))).willReturn(vo);

        mockMvc.perform(put("/admin/wishes/1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("PUT /admin/wishes/{id}/audit - 审核状态为空返回 400")
    void auditWish_blankStatus_returns400() throws Exception {
        AdminAuditWishRequest request = new AdminAuditWishRequest(null, null);

        mockMvc.perform(put("/admin/wishes/1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private AdminWishVO buildAdminWishVO() {
        return new AdminWishVO(
                1L, 1001L, "测试心愿", "描述", List.of(),
                100L, "学习成长", List.of(),
                com.cloudmart.wish.enums.WishVisibility.PUBLIC,
                com.cloudmart.wish.enums.WishStatus.ACTIVE,
                com.cloudmart.wish.enums.FruitType.GLOW,
                AuditStatus.PENDING,
                com.cloudmart.wish.enums.AuditStrategy.LAZY,
                true, 0, 0, 0, 0,
                null, null, LocalDateTime.now(), LocalDateTime.now(), null
        );
    }
}
