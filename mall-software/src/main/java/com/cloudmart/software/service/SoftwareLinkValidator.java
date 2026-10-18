package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SoftwareSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 下载来源链接校验与指纹（方案 5.1，LinkValidator）：
 * 仅语法校验，不访问网络/不追踪重定向/不展开短链——校验通过不代表外链可用，
 * 页面不得宣传"已验证可用"。
 *
 * <p>规则：HTTPS 拒绝 userinfo/空 host/控制字符/javascript|data|file|ftp 协议/
 * localhost/IP 字面量；网盘按 host 精确匹配（含合法子域，禁止 endsWith 误匹配
 * evilbaidu.com），其他网盘域名只能经人工审核启用；磁力首期仅 BTIH
 * 40 位十六进制或 32 位 Base32，允许 xt/dn/tr/xl，拒绝未知执行型参数；
 * 提取码/解压密码纯文本 trim 不转大小写；指纹=规范化 URL+提取码+解压密码 SHA-256。</p>
 */
@Slf4j
@Component
public class SoftwareLinkValidator {

    private static final int MAX_HTTPS_LENGTH = 4096;
    private static final int MAX_MAGNET_LENGTH = 8192;
    private static final Pattern BTIH_HEX = Pattern.compile("^urn:btih:[0-9a-fA-F]{40}$");
    private static final Pattern BTIH_BASE32 = Pattern.compile("^urn:btih:[A-Za-z2-7]{32}$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern IP_LITERAL = Pattern.compile(
            "^(\\d{1,3}\\.){3}\\d{1,3}$|^\\[[0-9a-fA-F:]+]$");
    private static final Set<String> ALLOWED_MAGNET_PARAMS = Set.of("xt", "dn", "tr", "xl");
    private static final Pattern LOCAL_HOST = Pattern.compile(
            "^(localhost|.*\\.local|.*\\.internal|.*\\.lan|.*\\.intranet)$");

    /** 网盘 host → 服务商（精确 host 及其一级子域匹配） */
    private static final Map<String, SoftwareProvider> NETDISK_HOSTS = Map.ofEntries(
            Map.entry("pan.baidu.com", SoftwareProvider.BAIDU),
            Map.entry("aliyundrive.com", SoftwareProvider.ALIYUN),
            Map.entry("alipan.com", SoftwareProvider.ALIYUN),
            Map.entry("pan.quark.cn", SoftwareProvider.QUARK),
            Map.entry("pan.uc.cn", SoftwareProvider.UC),
            Map.entry("1drv.ms", SoftwareProvider.ONEDRIVE),
            Map.entry("onedrive.live.com", SoftwareProvider.ONEDRIVE),
            Map.entry("sharepoint.com", SoftwareProvider.ONEDRIVE));

    /** @return 校验并规范化后的来源网络事实（url 为原始 trim 值，normalized 供哈希） */
    public ValidatedLink validate(SoftwareSourceType sourceType, String provider,
                                  String url, String extractionCode, String archivePassword) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "来源链接不能为空");
        }
        if (CONTROL_CHARS.matcher(url).find()) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "来源链接包含控制字符");
        }
        return switch (sourceType) {
            case NETDISK -> validateNetdisk(provider, url.trim(), extractionCode, archivePassword);
            case MAGNET -> validateMagnet(url.trim(), extractionCode, archivePassword);
            case TORRENT_URL -> validateTorrentUrl(url.trim(), extractionCode, archivePassword);
            // SF01：官方 HTTPS 来源——不限制网盘域名白名单，但必须 HTTPS 且主机可解析格式；
            // provider 由服务端裁定为 OTHER，不接受作者自标（官方认证须平台单独审核）。
            case HTTPS_DOWNLOAD -> validateOfficialHttps(url.trim(), extractionCode, archivePassword);
            case OFFICIAL_PAGE -> validateOfficialHttps(url.trim(), extractionCode, archivePassword);
            case WEB_APP -> validateOfficialHttps(url.trim(), extractionCode, archivePassword);
        };
    }

    private ValidatedLink validateNetdisk(String providerRaw, String url,
                                          String extractionCode, String archivePassword) {
        URI uri = parseHttps(url, MAX_HTTPS_LENGTH);
        SoftwareProvider resolved = resolveNetdiskProvider(uri.getHost(), providerRaw);
        String normalized = normalize(uri);
        String code = extractionCode == null ? null : extractionCode.trim();
        String password = archivePassword == null ? null : archivePassword.trim();
        return new ValidatedLink(url, normalized, sha256(normalized),
                fingerprint(normalized, code, password), code, password, resolved);
    }

    /** host 与其一级子域精确匹配网盘域名；未知域名拒绝（只能人工审核启用，5.1） */
    private SoftwareProvider resolveNetdiskProvider(String host, String providerRaw) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        SoftwareProvider mapped = NETDISK_HOSTS.entrySet().stream()
                .filter(entry -> normalizedHost.equals(entry.getKey())
                        || normalizedHost.equals("www." + entry.getKey())
                        || normalizedHost.endsWith("." + entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (mapped == null) {
            throw new BusinessException("SOFTWARE_LINK_INVALID",
                    "暂不支持该网盘域名，可使用受支持网盘或经管理员审核后启用");
        }
        if (providerRaw != null && !providerRaw.isBlank()) {
            SoftwareProvider claimed = SoftwareProvider.valueOf(providerRaw.toUpperCase(Locale.ROOT));
            if (claimed != mapped) {
                // provider 由服务端域名映射裁定，不信任客户端标签（5.1）
                throw new BusinessException("SOFTWARE_LINK_INVALID", "来源服务商与链接域名不符");
            }
        }
        return mapped;
    }

    /**
     * SF01：官方 HTTPS 来源（HTTPS_DOWNLOAD / OFFICIAL_PAGE / WEB_APP 共用）。
     * 仅限 HTTPS + 可解析主机格式；拒绝内网/回环/IP 字面量与云元数据地址。
     * 不做服务器端抓取（5.1），可达性由人工核验/巡检（SF07）另计。
     */
    private ValidatedLink validateOfficialHttps(String url, String extractionCode,
                                                String archivePassword) {
        URI uri = parseHttps(url, MAX_HTTPS_LENGTH);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || IP_LITERAL.matcher(host).matches()
                || LOCAL_HOST.matcher(host).matches()
                || host.endsWith(".localhost") || host.endsWith(".internal")
                || host.equals("169.254.169.254") || host.endsWith(".cloud.internal")) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "来源主机不可用（须为公网域名）");
        }
        String normalized = normalize(uri);
        String code = extractionCode == null ? null : extractionCode.trim();
        String password = archivePassword == null ? null : archivePassword.trim();
        return new ValidatedLink(url, normalized, sha256(normalized),
                fingerprint(normalized, code, password), code, password, SoftwareProvider.OTHER);
    }

    private ValidatedLink validateMagnet(String url, String extractionCode, String archivePassword) {
        if (url.length() > MAX_MAGNET_LENGTH) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "磁力链接过长（≤8192）");
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("magnet:?")) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "磁力链接格式非法");
        }
        String query = url.substring("magnet:?".length());
        int xtCount = 0;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (!ALLOWED_MAGNET_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("SOFTWARE_LINK_INVALID", "磁力链接包含不支持的参数: " + key);
            }
            if ("xt".equalsIgnoreCase(key)) {
                xtCount++;
                // value 形如 urn:btih:<btih>（URL 原样），直接按带前缀模式匹配
                String value = pair.substring(pair.indexOf('=') + 1);
                if (!BTIH_HEX.matcher(value).matches()
                        && !BTIH_BASE32.matcher(value).matches()) {
                    throw new BusinessException("SOFTWARE_LINK_INVALID",
                            "磁力 xt 仅接受 40 位十六进制或 32 位 Base32 BTIH");
                }
            }
        }
        if (xtCount != 1) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "磁力链接须恰好包含一个有效 xt/BTIH");
        }
        // 磁力为稳定语义标识，规范化仅统一协议头大小写
        String normalized = "magnet:?" + query;
        String code = extractionCode == null ? null : extractionCode.trim();
        String password = archivePassword == null ? null : archivePassword.trim();
        return new ValidatedLink(url, normalized, sha256(normalized),
                fingerprint(normalized, code, password), code, password, SoftwareProvider.OTHER);
    }

    private ValidatedLink validateTorrentUrl(String url, String extractionCode, String archivePassword) {
        URI uri = parseHttps(url, MAX_HTTPS_LENGTH);
        String normalized = normalize(uri);
        String code = extractionCode == null ? null : extractionCode.trim();
        String password = archivePassword == null ? null : archivePassword.trim();
        return new ValidatedLink(url, normalized, sha256(normalized),
                fingerprint(normalized, code, password), code, password, SoftwareProvider.OTHER);
    }

    /** HTTPS 统一解析：拒绝 userinfo/空 host/localhost/IP 字面量，标准化默认端口（5.1） */
    private URI parseHttps(String url, int maxLength) {
        if (url.length() > maxLength) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "链接过长（≤" + maxLength + "）");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "链接格式非法");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "外链仅接受 https");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "链接不允许携带 userinfo");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "链接缺少主机名");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (IP_LITERAL.matcher(lowerHost).matches() || LOCAL_HOST.matcher(lowerHost).matches()) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "不允许指向内网或本机地址");
        }
        return uri;
    }

    /** 标准化：小写 scheme/host、去除默认端口、去除 fragment（5.1"标准化域名和默认端口"） */
    private String normalize(URI uri) {
        int port = uri.getPort();
        StringBuilder builder = new StringBuilder("https://")
                .append(uri.getHost().toLowerCase(Locale.ROOT));
        if (port != -1 && port != 443) {
            builder.append(':').append(port);
        }
        if (uri.getRawPath() != null) {
            builder.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            builder.append('?').append(uri.getRawQuery());
        }
        return builder.toString();
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 来源内容指纹：规范化 URL + 提取码 + 解压密码（5.1）；secret 空串参与拼接保序 */
    public String fingerprint(String normalizedUrl, String extractionCode, String archivePassword) {
        return sha256(normalizedUrl + "|"
                + (extractionCode == null ? "" : extractionCode) + "|"
                + (archivePassword == null ? "" : archivePassword));
    }

    /**
     * @param url            原始链接（trim 后保存）
     * @param normalizedUrl  规范化链接
     * @param urlHash        规范化 URL 哈希（去重键）
     * @param contentFingerprint 内容指纹（URL+提取码+密码；核实结论按此匹配）
     * @param extractionCode 提取码（trim；纯文本）
     * @param archivePassword 解压密码（trim；纯文本）
     * @param provider       服务端裁定的服务商
     */
    public record ValidatedLink(String url, String normalizedUrl, String urlHash,
                                String contentFingerprint, String extractionCode,
                                String archivePassword, SoftwareProvider provider) {
    }
}
