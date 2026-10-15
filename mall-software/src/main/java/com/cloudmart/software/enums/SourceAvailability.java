package com.cloudmart.software.enums;

/**
 * 来源可用性（方案 4.2/4.3）：前四项为核实结论；DISABLED 为平台强制禁用。
 * 非平台禁用结论仅在 checked_fingerprint 与当前来源指纹相等时生效，否则展示 UNKNOWN。
 */
public enum SourceAvailability {
    UNKNOWN,
    AVAILABLE,
    SUSPECTED,
    INVALID,
    DISABLED
}
