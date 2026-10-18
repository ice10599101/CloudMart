package com.cloudmart.software.enums;

/** 来源目标架构（方案 6.5） */
public enum SoftwareArch {
    X64,
    X86,
    ARM64,
    UNIVERSAL,
    OTHER,
    /** SF01：Web 应用（在线使用，无安装架构语义） */
    ANY
}
