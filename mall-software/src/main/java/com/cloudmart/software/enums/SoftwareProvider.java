package com.cloudmart.software.enums;

/**
 * 网盘服务商（方案 5.1）：由服务端域名映射或审核确认，不信任客户端标签；
 * 其他网盘域名只能经人工审核启用。
 */
public enum SoftwareProvider {
    BAIDU,
    ALIYUN,
    QUARK,
    UC,
    ONEDRIVE,
    OTHER
}
