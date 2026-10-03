package com.cloudmart.gen.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.gen.dto.GenConfigRequest;
import com.cloudmart.gen.dto.GenPreviewResponse;
import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;
import com.cloudmart.gen.repository.GenTableMapper;
import com.cloudmart.gen.service.GenService;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GenServiceImpl implements GenService {

    private final GenTableMapper genTableMapper;

    @Value("${gen.package-name:com.cloudmart}")
    private String packageName;

    @Value("${gen.auto-remove-prefix:true}")
    private boolean autoRemovePrefix;

    @Value("${gen.table-prefix:}")
    private String tablePrefix;

    private VelocityEngine velocityEngine;

    public GenServiceImpl(GenTableMapper genTableMapper) {
        this.genTableMapper = genTableMapper;
    }

    @PostConstruct
    void initVelocity() {
        Properties props = new Properties();
        props.setProperty("resource.loader", "string");
        props.setProperty("string.resource.loader.class", "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        props.setProperty("string.resource.loader.repository.class", "org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl");
        velocityEngine = new VelocityEngine(props);
    }

    @Override
    public List<GenTableResponse> listTables() {
        return genTableMapper.selectTableList();
    }

    @Override
    public GenTableResponse getTable(String tableName) {
        GenTableResponse table = genTableMapper.selectTableByName(tableName);
        if (table == null) {
            throw new BusinessException("TABLE_NOT_FOUND", "表不存在: " + tableName);
        }
        return table;
    }

    @Override
    public List<GenTableColumnResponse> getTableColumns(String tableName) {
        return enrichColumns(genTableMapper.selectTableColumns(tableName));
    }

    @Override
    public List<GenPreviewResponse> preview(GenConfigRequest config) {
        VelocityContext context = buildContext(config);
        List<GenPreviewResponse> previews = new ArrayList<>();

        Map<String, String> templates = getTemplateMap(config);
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(context, writer, entry.getKey(), entry.getValue());
            previews.add(new GenPreviewResponse(
                    entry.getKey(),
                    safeEntryName(getFileName(entry.getKey(), context)),
                    writer.toString()
            ));
        }
        return previews;
    }

    @Override
    public byte[] generateCode(GenConfigRequest config) {
        VelocityContext context = buildContext(config);
        Map<String, String> templates = getTemplateMap(config);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(outputStream)) {

            for (Map.Entry<String, String> entry : templates.entrySet()) {
                StringWriter writer = new StringWriter();
                velocityEngine.evaluate(context, writer, entry.getKey(), entry.getValue());

                String fileName = safeEntryName(getFileName(entry.getKey(), context));
                zip.putNextEntry(new ZipEntry(fileName));
                zip.write(writer.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }

            zip.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("GEN_CODE_FAILED", "代码生成失败: " + e.getMessage());
        }
    }

    private VelocityContext buildContext(GenConfigRequest config) {
        String tableName = config.tableName();
        String pkg = config.packageName() != null ? config.packageName() : packageName;
        String moduleName = config.moduleName() != null ? config.moduleName() : "admin";
        String businessName = config.businessName() != null ? config.businessName() : toCamelCase(removePrefix(tableName));
        String functionName = config.functionName() != null ? config.functionName() : businessName;

        List<GenTableColumnResponse> columns = enrichColumns(genTableMapper.selectTableColumns(tableName));
        GenTableResponse table = genTableMapper.selectTableByName(tableName);
        if (table == null) {
            throw new BusinessException("TABLE_NOT_FOUND", "表不存在: " + tableName);
        }

        VelocityContext context = new VelocityContext();
        context.put("packageName", pkg);
        context.put("moduleName", moduleName);
        context.put("businessName", businessName);
        context.put("BusinessName", capitalize(businessName));
        context.put("functionName", functionName);
        context.put("tableName", tableName);
        context.put("tableComment", table.tableComment());
        context.put("columns", columns);
        context.put("pkColumn", columns.stream().filter(c -> "PRI".equals(c.columnKey())).findFirst().orElse(null));
        context.put("importList", buildImportList(columns));

        return context;
    }

    private String removePrefix(String tableName) {
        String prefix = tablePrefix;
        if (prefix != null && !prefix.isEmpty() && tableName.startsWith(prefix)) {
            return tableName.substring(prefix.length());
        }
        return tableName;
    }

    private String toCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                nextUpper = false;
            }
        }
        return result.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private List<GenTableColumnResponse> enrichColumns(List<GenTableColumnResponse> rawColumns) {
        return rawColumns.stream()
                .map(col -> new GenTableColumnResponse(
                        col.columnName(),
                        col.columnComment(),
                        col.columnType(),
                        col.columnKey(),
                        col.isNullable(),
                        col.columnDefault(),
                        col.extra(),
                        col.ordinalPosition(),
                        mapSqlTypeToJava(col.columnType()),
                        toJavaField(col.columnName())
                ))
                .toList();
    }

    private String mapSqlTypeToJava(String columnType) {
        if (columnType == null) return "String";
        String lower = columnType.toLowerCase();
        if (lower.startsWith("bigint")) return "Long";
        if (lower.startsWith("int")) return "Integer";
        if (lower.startsWith("tinyint") || lower.startsWith("smallint")) return "Integer";
        if (lower.startsWith("decimal") || lower.startsWith("numeric")) return "BigDecimal";
        if (lower.startsWith("datetime") || lower.startsWith("timestamp")) return "LocalDateTime";
        if (lower.startsWith("date")) return "LocalDate";
        return "String";
    }

    private String toJavaField(String columnName) {
        return toCamelCase(columnName);
    }

    private Set<String> buildImportList(List<GenTableColumnResponse> columns) {
        Set<String> imports = new LinkedHashSet<>();
        for (GenTableColumnResponse col : columns) {
            String type = col.columnType();
            if (type.startsWith("bigint") || type.startsWith("int")) imports.add("java.lang.Long");
            else if (type.startsWith("tinyint") || type.startsWith("smallint")) imports.add("java.lang.Integer");
            else if (type.startsWith("decimal") || type.startsWith("numeric")) imports.add("java.math.BigDecimal");
            else if (type.startsWith("datetime") || type.startsWith("timestamp")) imports.add("java.time.LocalDateTime");
            else if (type.startsWith("date")) imports.add("java.time.LocalDate");
        }
        return imports;
    }

    private Map<String, String> getTemplateMap(GenConfigRequest config) {
        Map<String, String> templates = new LinkedHashMap<>();
        String businessName = config.businessName() != null ? config.businessName() : toCamelCase(removePrefix(config.tableName()));
        String BusinessName = capitalize(businessName);

        templates.put("entity.java", """
package ${packageName}.${moduleName}.entity;

#foreach($imp in $importList)
import ${imp};
#end
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("${tableName}")
public class ${BusinessName} {
#if($pkColumn)
    @TableId(type = IdType.ASSIGN_ID)
    private ${pkColumn.javaType} ${pkColumn.javaField};
#end
#foreach($column in $columns)
#if($column.columnKey != 'PRI')
    private ${column.javaType} ${column.javaField};
#end
#end
}
""");

        templates.put("mapper.java", """
package ${packageName}.${moduleName}.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ${packageName}.${moduleName}.entity.${BusinessName};
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ${BusinessName}Mapper extends BaseMapper<${BusinessName}> {}
""");

        templates.put("service.java", """
package ${packageName}.${moduleName}.service;

import ${packageName}.${moduleName}.entity.${BusinessName};
import java.util.List;

public interface ${BusinessName}Service {
    List<${BusinessName}> list();
    ${BusinessName} getById(Long id);
    Long create(${BusinessName} entity);
    void update(Long id, ${BusinessName} entity);
    void delete(Long id);
}
""");

        templates.put("controller.java", """
package ${packageName}.${moduleName}.controller;

import ${packageName}.${moduleName}.entity.${BusinessName};
import ${packageName}.${moduleName}.service.${BusinessName}Service;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/${businessName}")
@Tag(name = "${functionName}管理", description = "${functionName}增删改查")
public class ${BusinessName}Controller {

    private final ${BusinessName}Service service;

    public ${BusinessName}Controller(${BusinessName}Service service) {
        this.service = service;
    }

    @GetMapping("/list")
    @RequiresPermission("${moduleName}:${businessName}:list")
    @Operation(summary = "${functionName}列表")
    public ApiResponse<List<${BusinessName}>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("${moduleName}:${businessName}:query")
    @Operation(summary = "${functionName}详情")
    public ApiResponse<${BusinessName}> getInfo(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("${moduleName}:${businessName}:add")
    @Operation(summary = "新增${functionName}")
    public ApiResponse<Long> add(@RequestBody ${BusinessName} entity) {
        return ApiResponse.ok(service.create(entity));
    }

    @PutMapping("/{id}")
    @RequiresPermission("${moduleName}:${businessName}:edit")
    @Operation(summary = "修改${functionName}")
    public ApiResponse<Void> edit(@PathVariable Long id, @RequestBody ${BusinessName} entity) {
        service.update(id, entity);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("${moduleName}:${businessName}:remove")
    @Operation(summary = "删除${functionName}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
""");

        templates.put("api.ts", """
// E04 生成物：React API 客户端（types + CRUD）——页面组件按项目规范人工编写
import request from '@/utils/request'

export interface ${BusinessName}Record {
  id: number | string
  [key: string]: unknown
}

export function list${BusinessName}(params: { page?: number; pageSize?: number }) {
  return request.get('/${moduleName}/${businessName}/list', { params })
}

export function get${BusinessName}(id: number | string) {
  return request.get(`/${moduleName}/${businessName}/${'$'}{id}`)
}

export function create${BusinessName}(data: Partial<${BusinessName}Record>) {
  return request.post('/${moduleName}/${businessName}', data)
}

export function update${BusinessName}(id: number | string, data: Partial<${BusinessName}Record>) {
  return request.put(`/${moduleName}/${businessName}/${'$'}{id}`, data)
}

export function delete${BusinessName}(id: number | string) {
  return request.delete(`/${moduleName}/${businessName}/${'$'}{id}`)
}
""");

        return templates;
    }

    private String getFileName(String templateName, VelocityContext context) {
        String businessName = (String) context.get("businessName");
        String BusinessName = (String) context.get("BusinessName");
        String moduleName = (String) context.get("moduleName");
        String packageName = (String) context.get("packageName");
        String packagePath = packageName.replace('.', '/');

        return switch (templateName) {
            case "entity.java" -> String.format("java/%s/%s/entity/%s.java", packagePath, moduleName, BusinessName);
            case "mapper.java" -> String.format("java/%s/%s/repository/%sMapper.java", packagePath, moduleName, BusinessName);
            case "service.java" -> String.format("java/%s/%s/service/%sService.java", packagePath, moduleName, BusinessName);
            case "controller.java" -> String.format("java/%s/%s/controller/%sController.java", packagePath, moduleName, BusinessName);
            // E04：三端均为 React——原 index.vue 模板已删除，改为生成 API 客户端
            case "api.ts" -> String.format("web/api/%s/%s.ts", moduleName, businessName);
            default -> templateName;
        };
    }

    /** E04：ZIP entry 兜底校验——拒绝路径穿越/绝对路径/反斜杠/控制字符 */
    private String safeEntryName(String name) {
        if (name == null || name.isBlank()
                || name.startsWith("/") || name.startsWith("\\")
                || name.contains("..") || name.contains("\\")
                || name.indexOf(':') >= 0
                || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new BusinessException("GEN_PATH_INVALID", "生成文件路径非法: " + name);
        }
        return name;
    }
}
