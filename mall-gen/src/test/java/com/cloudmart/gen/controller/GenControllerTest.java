package com.cloudmart.gen.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.gen.dto.GenConfigRequest;
import com.cloudmart.gen.dto.GenPreviewResponse;
import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;
import com.cloudmart.gen.service.GenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GenControllerTest {

    private MockMvc mockMvc;

    private final GenService genService = Mockito.mock(GenService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GenController(genService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("查询数据库表 - 成功返回信封")
    void listTables_ShouldReturnEnvelope() throws Exception {
        GenTableResponse table = new GenTableResponse("t_product", "商品表", FIXED_TIME, FIXED_TIME);

        given(genService.listTables()).willReturn(List.of(table));

        mockMvc.perform(get("/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].tableName").value("t_product"))
                .andExpect(jsonPath("$.data[0].tableComment").value("商品表"));
    }

    @Test
    @DisplayName("查询表结构 - 成功返回信封")
    void getTableDetail_ShouldReturnEnvelope() throws Exception {
        GenTableResponse table = new GenTableResponse("t_product", "商品表", FIXED_TIME, FIXED_TIME);
        GenTableColumnResponse column = new GenTableColumnResponse(
                "id", "主键", "BIGINT", "PRI", "NO", null, "auto_increment", 1, "Long", "id");

        given(genService.getTable("t_product")).willReturn(table);
        given(genService.getTableColumns("t_product")).willReturn(List.of(column));

        mockMvc.perform(get("/tables/t_product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.table.tableName").value("t_product"))
                .andExpect(jsonPath("$.data.columns").isArray())
                .andExpect(jsonPath("$.data.columns[0].columnName").value("id"));
    }

    @Test
    @DisplayName("预览代码 - 成功返回信封")
    void preview_ShouldReturnEnvelope() throws Exception {
        GenPreviewResponse preview = new GenPreviewResponse("Controller.java", "ProductController.java", "package com.example;");

        given(genService.preview(Mockito.any(GenConfigRequest.class))).willReturn(List.of(preview));

        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableName\":\"t_product\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].templateName").value("Controller.java"))
                .andExpect(jsonPath("$.data[0].fileName").value("ProductController.java"));
    }

    @Test
    @DisplayName("预览代码 - 缺少必填字段返回校验错误")
    void preview_WhenMissingTableName_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
