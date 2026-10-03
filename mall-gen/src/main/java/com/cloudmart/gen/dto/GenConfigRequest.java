package com.cloudmart.gen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * E04：生成配置标识符白名单——tableName/moduleName/businessName/packageName
 * 进入生成文本与 ZIP entry 路径，仅允许安全标识符字符；functionName 仅进生成
 * 文案，限长不参与路径。
 */
public record GenConfigRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$", message = "表名仅允许字母/数字/下划线")
    String tableName,
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$", message = "包名仅允许小写标识符与点分段")
    String packageName,
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{0,31}$", message = "模块名仅允许小写标识符")
    String moduleName,
    @Pattern(regexp = "^$|^[a-z][a-zA-Z0-9_]{0,63}$", message = "业务名仅允许安全标识符字符")
    String businessName,
    @Size(max = 64, message = "功能名过长")
    String functionName,
    @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_]{0,31}$", message = "表前缀仅允许字母/数字/下划线")
    String tablePrefix
) {}
