package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.AdminUserDTO;
import com.cloudmart.auth.dto.LoginLogRecordRequest;
import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.AdminLoginLogFeignClient;
import com.cloudmart.auth.feign.AdminUserFeignClient;
import com.cloudmart.auth.service.AdminAuthService;
import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.RefreshTokenService.RotationResult;
import com.cloudmart.auth.service.SubjectType;
import com.cloudmart.auth.util.JwtProvider.TokenPrincipal;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthServiceImpl.class);

    private final AdminUserFeignClient adminUserFeignClient;
    private final AdminLoginLogFeignClient loginLogFeignClient;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionService authSessionService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long accessTokenExpiration;
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    public AdminAuthServiceImpl(AdminUserFeignClient adminUserFeignClient,
                                AdminLoginLogFeignClient loginLogFeignClient,
                                JwtProvider jwtProvider,
                                RefreshTokenService refreshTokenService,
            AuthSessionService authSessionService,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration) {
        this.adminUserFeignClient = adminUserFeignClient;
        this.loginLogFeignClient = loginLogFeignClient;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.authSessionService = authSessionService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getRemoteIp(httpRequest);
        String browser = parseBrowser(httpRequest.getHeader("User-Agent"));
        String os = parseOs(httpRequest.getHeader("User-Agent"));
        String account = request.account();

        // 账号锁定校验：防止暴力破解
        String lockValue = redisTemplate.opsForValue().get(SecurityConstants.ADMIN_LOCK_PREFIX + account);
        if ("1".equals(lockValue)) {
            throw new BusinessException("ACCOUNT_LOCKED", "账号已锁定，请稍后重试");
        }

        ValidateRequest validateRequest = new ValidateRequest(account, request.password());
        ApiResponse<AdminUserDTO> response;
        try {
            response = adminUserFeignClient.validateAdmin(validateRequest);
        } catch (FeignException e) {
            log.warn("Feign call to mall-admin validate failed: status={}, message={}",
                    e.status(), e.getMessage());
            recordLoginFailure(account, ip, browser, os);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        if (!response.success() || response.data() == null) {
            recordLoginFailure(account, ip, browser, os);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误");
        }

        // 登录成功，清除失败计数
        redisTemplate.delete(SecurityConstants.ADMIN_LOGIN_FAIL_PREFIX + account);

        AdminUserDTO admin = response.data();
        Set<String> permissions = admin.isSuperAdmin() ? Set.of("*:*:*") : admin.permissions();
        // SEC-03：签发可撤销会话，令牌携带 sid + 当前认证状态版本
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.ADMIN, admin.id());
        String accessToken = jwtProvider.generateAdminAccessToken(
                new TokenPrincipal(SubjectType.ADMIN, admin.id(), session.sid(), session.authVersion()),
                permissions, admin.username(), admin.deptId());
        // SEC-02：管理员刷新家族绑定会话，"退出当前设备"只撤销本设备
        String refreshToken = refreshTokenService.createRefreshToken(SubjectType.ADMIN, admin.id(), session.sid());

        String tokenId = UUID.randomUUID().toString();
        storeOnlineUser(tokenId, admin.id(), admin.username(), ip, browser);

        recordLoginLogSafe(account, ip, browser, os, 0, "登录成功");

        return new LoginResponse(accessToken, refreshToken, "Bearer", accessTokenExpiration, tokenId);
    }

    @Override
    public LoginResponse refresh(String refreshTokenValue) {
        // SEC-02：仅接受管理员域（a:）令牌；普通用户令牌在此被拒且不消费；
        // 轮换在原家族内原子推进，不再整发新家族（杜绝无限续期）。
        RotationResult rotation =
                refreshTokenService.rotateRefreshToken(SubjectType.ADMIN, refreshTokenValue);
        Long userId = rotation.subjectId();

        AdminUserInfo userInfo = fetchUserInfo(userId);
        // SEC-03：刷新签发新会话并携带最新认证状态版本（权限软失效后自动拿到新权限集）
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.ADMIN, userId);
        String accessToken = jwtProvider.generateAdminAccessToken(
                new TokenPrincipal(SubjectType.ADMIN, userId, session.sid(), session.authVersion()),
                userInfo.permissions, userInfo.username, userInfo.deptId);

        // SEC-02：家族重绑新会话并撤销旧会话（同家族同一时刻只有一个活动 sid）
        String previousSid = refreshTokenService.bindFamilySession(rotation.tokenValue(), session.sid());
        if (previousSid != null) {
            authSessionService.revokeSession(previousSid);
        }

        refreshOnlineUserTtl(userId);

        return new LoginResponse(accessToken, rotation.tokenValue(), "Bearer", accessTokenExpiration, null);
    }

    @Override
    public void logout(Long userId, String sid) {
        removeOnlineUser(userId);
        // SEC-02：撤销当前会话 + 绑定该会话的管理员刷新家族（其他设备不受影响）；
        // sid 缺失时保守撤销全部刷新家族，绝不伪造成已撤销的无操作。
        authSessionService.revokeSession(sid);
        if (sid == null || sid.isBlank()) {
            refreshTokenService.revokeAllTokensForSubject(SubjectType.ADMIN, userId);
            return;
        }
        refreshTokenService.revokeFamilyBySession(SubjectType.ADMIN, userId, sid);
    }

    @Override
    public void logoutAll(Long userId) {
        // SEC-02：版本递增使全部管理员访问令牌秒级失效，撤销全部刷新家族阻断续期
        removeOnlineUser(userId);
        authSessionService.invalidate(SubjectType.ADMIN, userId, true);
    }

    private void storeOnlineUser(String tokenId, Long userId, String username, String ip, String browser) {
        try {
            String key = SecurityConstants.ADMIN_ONLINE_PREFIX + tokenId;
            Map<String, String> onlineInfo = Map.of(
                    "userId", userId.toString(),
                    "username", username,
                    "loginTime", LocalDateTime.now().toString(),
                    "ipaddr", ip != null ? ip : "未知",
                    "browser", browser != null ? browser : "未知"
            );
            String value = objectMapper.writeValueAsString(onlineInfo);
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(accessTokenExpiration));
        } catch (JacksonException e) {
            log.warn("Failed to store online user info: {}", e.getMessage());
        }
    }

    private void refreshOnlineUserTtl(Long userId) {
        try {
            String key = findOnlineUserKey(userId);
            if (key != null) {
                redisTemplate.expire(key, Duration.ofSeconds(accessTokenExpiration));
            }
        } catch (Exception e) {
            log.warn("Failed to refresh online user TTL: {}", e.getMessage());
        }
    }

    private void removeOnlineUser(Long userId) {
        try {
            String key = findOnlineUserKey(userId);
            if (key != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("Failed to remove online user: {}", e.getMessage());
        }
    }

    private String findOnlineUserKey(Long userId) {
        List<String> keys = scanOnlineKeys();
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && value.contains("\"userId\":\"" + userId + "\"")) {
                return key;
            }
        }
        return null;
    }

    private List<String> scanOnlineKeys() {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(SecurityConstants.ADMIN_ONLINE_PREFIX + "*")
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next()));
                }
            }
            return result;
        });
        return keys != null ? new ArrayList<>(keys) : List.of();
    }

    private void recordLoginFailure(String account, String ip, String browser, String os) {
        Long failCount = redisTemplate.opsForValue()
                .increment(SecurityConstants.ADMIN_LOGIN_FAIL_PREFIX + account);
        if (failCount != null && failCount >= MAX_LOGIN_FAIL_COUNT) {
            redisTemplate.opsForValue()
                    .set(SecurityConstants.ADMIN_LOCK_PREFIX + account, "1", LOCK_DURATION);
        }
        recordLoginLogSafe(account, ip, browser, os, 1, "用户名或密码错误");
    }

    private void recordLoginLogSafe(String username, String ip, String browser, String os, int status, String msg) {
        try {
            loginLogFeignClient.recordLogin(new LoginLogRecordRequest(
                    username, ip, parseLocation(ip), browser, os, status, msg
            ));
        } catch (Exception e) {
            log.warn("Failed to record login log: {}", e.getMessage());
        }
    }

    private String parseLocation(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "未知";
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return "内网IP";
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("0.")) {
            return "内网IP";
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return "内网IP";
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "未知";
    }

    private String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) {
            return "未知";
        }
        if (userAgent.contains("Edg/")) {
            return "Edge";
        }
        if (userAgent.contains("Chrome/") && !userAgent.contains("Edg/")) {
            return "Chrome";
        }
        if (userAgent.contains("Firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) {
            return "Safari";
        }
        if (userAgent.contains("MSIE") || userAgent.contains("Trident/")) {
            return "IE";
        }
        return "未知";
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) {
            return "未知";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Mac OS")) {
            return "Mac OS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            return "iOS";
        }
        return "未知";
    }

    private AdminUserInfo fetchUserInfo(Long userId) {
        try {
            ApiResponse<AdminUserDTO> response = adminUserFeignClient.getPermissionsByUserId(userId);
            if (response.success() && response.data() != null) {
                AdminUserDTO dto = response.data();
                Set<String> permissions = dto.isSuperAdmin() ? Set.of("*:*:*") : dto.permissions();
                return new AdminUserInfo(dto.username(), dto.deptId(), permissions);
            }
        } catch (Exception e) {
            throw new BusinessException("PERMISSION_FETCH_FAILED", "获取用户权限失败");
        }
        return new AdminUserInfo(null, null, Set.of());
    }

    private record AdminUserInfo(String username, Long deptId, Set<String> permissions) {}
}
