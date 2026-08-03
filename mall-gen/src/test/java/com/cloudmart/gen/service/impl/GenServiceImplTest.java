package com.cloudmart.gen.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.gen.dto.GenConfigRequest;
import com.cloudmart.gen.dto.GenPreviewResponse;
import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;
import com.cloudmart.gen.repository.GenTableMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenServiceImpl 单元测试")
class GenServiceImplTest {

    @Mock
    private GenTableMapper genTableMapper;

    @InjectMocks
    private GenServiceImpl genService;

    private GenTableResponse buildTableResponse() {
        return new GenTableResponse("sys_user", "用户表", LocalDateTime.now(), LocalDateTime.now());
    }

    private List<GenTableColumnResponse> buildColumns() {
        return List.of(
                new GenTableColumnResponse("id", "主键", "bigint", "PRI", "NO",
                        null, "auto_increment", 1, "Long", "id"),
                new GenTableColumnResponse("username", "用户名", "varchar(64)", "", "NO",
                        null, "", 2, "String", "username"),
                new GenTableColumnResponse("created_at", "创建时间", "datetime", "", "YES",
                        null, "", 3, "LocalDateTime", "createdAt")
        );
    }

    @BeforeEach
    void initVelocity() {
        genService.initVelocity();
    }

    @Nested
    @DisplayName("listTables 测试")
    class ListTablesTests {

        @Test
        @DisplayName("查询表列表 - 成功返回")
        void shouldListTables() {
            when(genTableMapper.selectTableList()).thenReturn(List.of(buildTableResponse()));

            var result = genService.listTables();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).tableName()).isEqualTo("sys_user");
        }

        @Test
        @DisplayName("查询表列表 - 无表时返回空列表")
        void shouldReturnEmptyList() {
            when(genTableMapper.selectTableList()).thenReturn(List.of());

            var result = genService.listTables();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTable 测试")
    class GetTableTests {

        @Test
        @DisplayName("查询表 - 成功返回")
        void shouldGetTable() {
            when(genTableMapper.selectTableByName("sys_user")).thenReturn(buildTableResponse());

            GenTableResponse result = genService.getTable("sys_user");

            assertThat(result).isNotNull();
            assertThat(result.tableName()).isEqualTo("sys_user");
        }

        @Test
        @DisplayName("查询表 - 不存在时抛异常")
        void shouldThrowWhenTableNotFound() {
            when(genTableMapper.selectTableByName("non_exist")).thenReturn(null);

            assertThatThrownBy(() -> genService.getTable("non_exist"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("TABLE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("getTableColumns 测试")
    class GetTableColumnsTests {

        @Test
        @DisplayName("查询表字段 - 成功返回并填充Java类型")
        void shouldGetTableColumnsWithJavaTypes() {
            when(genTableMapper.selectTableColumns("sys_user")).thenReturn(List.of(
                    new GenTableColumnResponse("id", "主键", "bigint", "PRI", "NO",
                            null, "auto_increment", 1, "", "")
            ));

            List<GenTableColumnResponse> result = genService.getTableColumns("sys_user");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).javaType()).isEqualTo("Long");
            assertThat(result.get(0).javaField()).isEqualTo("id");
        }

        @Test
        @DisplayName("查询表字段 - 多种SQL类型正确映射到Java类型")
        void shouldMapVariousSqlTypes() {
            when(genTableMapper.selectTableColumns("sys_user")).thenReturn(List.of(
                    new GenTableColumnResponse("id", "主键", "bigint", "PRI", "NO", null, "", 1, "", ""),
                    new GenTableColumnResponse("age", "年龄", "int", "", "NO", null, "", 2, "", ""),
                    new GenTableColumnResponse("price", "价格", "decimal(10,2)", "", "NO", null, "", 3, "", ""),
                    new GenTableColumnResponse("created_at", "创建时间", "datetime", "", "YES", null, "", 4, "", ""),
                    new GenTableColumnResponse("birth_date", "生日", "date", "", "YES", null, "", 5, "", ""),
                    new GenTableColumnResponse("name", "名称", "varchar(100)", "", "NO", null, "", 6, "", "")
            ));

            List<GenTableColumnResponse> result = genService.getTableColumns("sys_user");

            assertThat(result).hasSize(6);
            assertThat(result.get(0).javaType()).isEqualTo("Long");
            assertThat(result.get(1).javaType()).isEqualTo("Integer");
            assertThat(result.get(2).javaType()).isEqualTo("BigDecimal");
            assertThat(result.get(3).javaType()).isEqualTo("LocalDateTime");
            assertThat(result.get(4).javaType()).isEqualTo("LocalDate");
            assertThat(result.get(5).javaType()).isEqualTo("String");
        }
    }

    @Nested
    @DisplayName("preview 测试")
    class PreviewTests {

        @Test
        @DisplayName("预览代码 - 成功生成模板预览")
        void shouldPreviewCode() {
            GenConfigRequest config = new GenConfigRequest("sys_user", "com.cloudmart", "admin", "user", "用户", null);

            when(genTableMapper.selectTableColumns("sys_user")).thenReturn(buildColumns());
            when(genTableMapper.selectTableByName("sys_user")).thenReturn(buildTableResponse());

            List<GenPreviewResponse> result = genService.preview(config);

            assertThat(result).isNotEmpty();
            assertThat(result.stream().map(GenPreviewResponse::templateName).toList())
                    .contains("entity.java", "mapper.java", "service.java", "controller.java", "index.vue");
        }

        @Test
        @DisplayName("预览代码 - 表不存在时抛异常")
        void shouldThrowWhenTableNotFound() {
            GenConfigRequest config = new GenConfigRequest("non_exist", "com.cloudmart", "admin", null, null, null);

            when(genTableMapper.selectTableColumns("non_exist")).thenReturn(List.of());
            when(genTableMapper.selectTableByName("non_exist")).thenReturn(null);

            assertThatThrownBy(() -> genService.preview(config))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("TABLE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("generateCode 测试")
    class GenerateCodeTests {

        @Test
        @DisplayName("生成代码 - 成功返回ZIP字节数组")
        void shouldGenerateCodeAsZip() {
            GenConfigRequest config = new GenConfigRequest("sys_user", "com.cloudmart", "admin", "user", "用户", null);

            when(genTableMapper.selectTableColumns("sys_user")).thenReturn(buildColumns());
            when(genTableMapper.selectTableByName("sys_user")).thenReturn(buildTableResponse());

            byte[] result = genService.generateCode(config);

            assertThat(result).isNotEmpty();
            assertThat(result[0]).isEqualTo((byte) 0x50);
        }

        @Test
        @DisplayName("生成代码 - 表不存在时抛异常")
        void shouldThrowWhenTableNotFound() {
            GenConfigRequest config = new GenConfigRequest("non_exist", "com.cloudmart", "admin", null, null, null);

            when(genTableMapper.selectTableColumns("non_exist")).thenReturn(List.of());
            when(genTableMapper.selectTableByName("non_exist")).thenReturn(null);

            assertThatThrownBy(() -> genService.generateCode(config))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("TABLE_NOT_FOUND");
        }
    }
}
