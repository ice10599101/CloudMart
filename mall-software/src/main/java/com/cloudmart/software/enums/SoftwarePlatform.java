package com.cloudmart.software.enums;

/**
 * 目标平台（方案 6.5）：IOS 表示介绍中可支持该平台，不代表本站能安装任意 IPA。
 */
public enum SoftwarePlatform {
    WINDOWS,
    MACOS,
    LINUX,
    ANDROID,
    IOS,
    CROSS_PLATFORM,
    /** SF01：Web 应用（在线使用，无安装平台语义） */
    WEB
}
