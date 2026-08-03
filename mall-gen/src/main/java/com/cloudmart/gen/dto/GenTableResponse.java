package com.cloudmart.gen.dto;

import java.time.LocalDateTime;

public record GenTableResponse(
    String tableName,
    String tableComment,
    LocalDateTime createTime,
    LocalDateTime updateTime
) {}
