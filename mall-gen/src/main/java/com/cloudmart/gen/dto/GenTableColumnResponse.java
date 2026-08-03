package com.cloudmart.gen.dto;

public record GenTableColumnResponse(
    String columnName,
    String columnComment,
    String columnType,
    String columnKey,
    String isNullable,
    String columnDefault,
    String extra,
    Integer ordinalPosition,
    String javaType,
    String javaField
) {}
