package com.cloudmart.software.enums;

/**
 * 下载来源类型（方案 5.1）：只发布外部链接，不上传/托管安装包。
 */
public enum SoftwareSourceType {
    /** 网盘 HTTPS 分享地址 */
    NETDISK,
    /** 磁力链接（BTIH 40 位十六进制或 32 位 Base32） */
    MAGNET,
    /** HTTPS 种子外链（不强制 .torrent 结尾） */
    TORRENT_URL,
    /** SF01：HTTPS 直链安装包（获取安装包地址） */
    HTTPS_DOWNLOAD,
    /** SF01：官方发布页（前往官网；展示资料，不自动绕过受限来源门槛） */
    OFFICIAL_PAGE,
    /** SF01：在线使用（Web 应用；平台不适用，固定 WEB+ANY） */
    WEB_APP
}
