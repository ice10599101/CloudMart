package com.cloudmart.software.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.HttpURLConnection;
import java.util.Locale;

/**
 * SF07 来源健康巡检的受控 HTTP 探测组件。
 *
 * <p>安全约束（必要实现条件，非可选优化）：
 * <ul>
 *   <li>仅 HTTPS；端口仅 443；</li>
 *   <li>DNS 解析后校验目标 IP——拒绝回环/链路本地/站点本地/唯一本地/云元数据地址
 *       （含 169.254.169.254）；</li>
 *   <li>跟随重定向 ≤3 次，每次跳转对目标主机重新解析并重验（防 DNS 重绑定与内网跳转）；</li>
 *   <li>连接/读取超时各 5 秒；不读取响应体（HEAD 优先，仅凭状态码与 Content-Type 判定），
 *       不把首页 200 当作安装包可下载；</li>
 *   <li>不携带任何凭据/Cookie（凭据不得跟随跳转跨域）。</li>
 * </ul>
 * 限频/验证码/403 等 HTTP 层异常优先 UNKNOWN，不直接判死链。
 */
@Slf4j
@Component
public class SoftwareHttpProbe {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_REDIRECTS = 3;

    /** 巡检结论（与巡检表 chk_patrol_outcome 对齐） */
    public enum Outcome {
        AVAILABLE, INVALID, UNKNOWN
    }

    /** 探测结果：结论 + 原因码 + HTTP 状态 + 跳转次数 + 耗时 */
    public record ProbeResult(Outcome outcome, String reasonCode, Integer httpStatus,
                              int redirectCount, long elapsedMs) {
    }

    /**
     * 探测目标 URL（仅 HTTPS）。
     *
     * @throws IllegalArgumentException URL 非 HTTPS 或主机非法
     */
    public ProbeResult probe(String url) {
        long startedAt = System.currentTimeMillis();
        String current = url;
        int redirects = 0;
        while (true) {
            URI uri;
            try {
                uri = URI.create(current);
            } catch (IllegalArgumentException e) {
                return result(Outcome.INVALID, "URL_INVALID", null, redirects, startedAt);
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return result(Outcome.INVALID, "NOT_HTTPS", null, redirects, startedAt);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return result(Outcome.INVALID, "URL_INVALID", null, redirects, startedAt);
            }
            // DNS 解析后校验目标 IP（每次跳转重验，防 DNS 重绑定）
            InetAddress address;
            try {
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                return result(Outcome.INVALID, "DNS_FAILED", null, redirects, startedAt);
            }
            if (!isPublicAddress(address)) {
                log.warn("巡检拒绝非公网目标 host={} address={}", host, address.getHostAddress());
                return result(Outcome.INVALID, "FORBIDDEN_ADDRESS", null, redirects, startedAt);
            }
            int status = requestStatus(uri);
            if (status == -1) {
                return result(Outcome.UNKNOWN, "CONN_FAILED", null, redirects, startedAt);
            }
            if (status >= 300 && status < 400) {
                // requestStatus 已禁用自动跳转：单独读取 Location 并对目标重验
                String next = resolveRedirect(uri);
                if (next == null || redirects >= MAX_REDIRECTS) {
                    return result(Outcome.UNKNOWN, redirects >= MAX_REDIRECTS
                            ? "TOO_MANY_REDIRECTS" : "REDIRECT_NO_LOCATION", status,
                            redirects, startedAt);
                }
                current = next;
                redirects++;
                continue;
            }
            if (status == 429 || status == 403 || status == 401) {
                // 限频/验证码/需要登录：优先 UNKNOWN，不判死
                return result(Outcome.UNKNOWN, "RATE_LIMITED_OR_AUTH", status,
                        redirects, startedAt);
            }
            if (status >= 200 && status < 300) {
                // HTTPS_DOWNLOAD 不把首页 200 当可下载：text/html 视为 UNKNOWN（更可能是错误页）
                String contentType = contentTypeOf(uri);
                boolean htmlLike = contentType != null
                        && contentType.toLowerCase(Locale.ROOT).contains("text/html");
                return result(htmlLike ? Outcome.UNKNOWN : Outcome.AVAILABLE,
                        htmlLike ? "HTML_RESPONSE" : "OK", status, redirects, startedAt);
            }
            if (status == 404 || status == 410) {
                return result(Outcome.INVALID, "HTTP_ERROR", status, redirects, startedAt);
            }
            return result(Outcome.UNKNOWN, "HTTP_ERROR", status, redirects, startedAt);
        }
    }

    private ProbeResult result(Outcome outcome, String reasonCode, Integer status,
                               int redirects, long startedAt) {
        return new ProbeResult(outcome, reasonCode, status, redirects,
                System.currentTimeMillis() - startedAt);
    }

    /** 仅公网地址（回环/链路本地/站点本地/唯一本地/链路本地多播/云元数据全部拒绝） */
    private boolean isPublicAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        String host = address.getHostAddress();
        // 云元数据（AWS/GCP/Azure 等 169.254.0.0/16）与 IPv4 私有段兜底
        if (host.startsWith("169.254.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.startsWith("172.")) {
            return false;
        }
        // IPv6 ULA fc00::/7
        if (address.getAddress().length == 16) {
            int first = address.getAddress()[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                return false;
            }
        }
        return true;
    }

    /** 单次请求状态码（禁用自动跳转；失败返回 -1） */
    private int requestStatus(URI uri) {
        try {
            HttpURLConnection connection = openConnection(uri);
            return connection.getResponseCode();
        } catch (IOException e) {
            return -1;
        }
    }

    private HttpURLConnection openConnection(URI uri) {
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent",
                    "CloudMartSourcePatrol/1.0 (+health-check)");
            return connection;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String resolveRedirect(URI uri) {
        try {
            HttpURLConnection connection = openConnection(uri);
            connection.getResponseCode();
            return connection.getHeaderField("Location");
        } catch (IOException e) {
            return null;
        }
    }

    private String contentTypeOf(URI uri) {
        try {
            return openConnection(uri).getContentType();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
