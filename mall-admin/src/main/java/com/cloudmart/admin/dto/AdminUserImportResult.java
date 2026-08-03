package com.cloudmart.admin.dto;

import java.util.List;

public record AdminUserImportResult(
    int successCount,
    int failureCount,
    List<String> failureMessages
) {}
