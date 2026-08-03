package com.cloudmart.gen.controller;

import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.gen.dto.GenConfigRequest;
import com.cloudmart.gen.dto.GenPreviewResponse;
import com.cloudmart.gen.dto.GenTableColumnResponse;
import com.cloudmart.gen.dto.GenTableResponse;
import com.cloudmart.gen.service.GenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "代码生成", description = "读取表结构、预览代码、下载代码")
public class GenController {

    private final GenService genService;

    public GenController(GenService genService) {
        this.genService = genService;
    }

    @GetMapping("/tables")
    @RequiresPermission("tool:gen:list")
    @Operation(summary = "查询数据库表", description = "查询当前数据库中所有业务表")
    public ApiResponse<List<GenTableResponse>> listTables() {
        return ApiResponse.ok(genService.listTables());
    }

    @GetMapping("/tables/{tableName}")
    @RequiresPermission("tool:gen:query")
    @Operation(summary = "查询表结构", description = "查询指定表的列信息")
    public ApiResponse<GenTableDetailResponse> getTableDetail(@PathVariable String tableName) {
        GenTableResponse table = genService.getTable(tableName);
        List<GenTableColumnResponse> columns = genService.getTableColumns(tableName);
        return ApiResponse.ok(new GenTableDetailResponse(table, columns));
    }

    @PostMapping("/preview")
    @RequiresPermission("tool:gen:preview")
    @Operation(summary = "预览代码", description = "根据表结构预览生成的代码")
    public ApiResponse<List<GenPreviewResponse>> preview(@Valid @RequestBody GenConfigRequest config) {
        return ApiResponse.ok(genService.preview(config));
    }

    @PostMapping("/download")
    @RequiresPermission("tool:gen:code")
    @Operation(summary = "下载代码", description = "生成代码并打包下载")
    public ResponseEntity<byte[]> download(@Valid @RequestBody GenConfigRequest config) {
        byte[] data = genService.generateCode(config);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + config.tableName() + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    public record GenTableDetailResponse(GenTableResponse table, List<GenTableColumnResponse> columns) {}
}
