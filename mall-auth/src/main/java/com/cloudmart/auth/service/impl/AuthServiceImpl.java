package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.UserFeignClient;
import com.cloudmart.auth.service.AuthService;
import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.RefreshTokenService.RotationResult;
import com.cloudmart.auth.service.SubjectType;
import com.cloudmart.auth.util.JwtProvider.TokenPrincipal;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(com.cloudmart.common.time.UserBusinessCalendar.DEFAULT_ZONE_ID);

    private final UserFeignClient userFeignClient;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionService authSessionService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long accessTokenExpiration;
    private final Clock clock;

    @Autowired
    public AuthServiceImpl(UserFeignClient userFeignClient,
                           JwtProvider jwtProvider,
                           RefreshTokenService refreshTokenService,
                           AuthSessionService authSessionService,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration,
                           Clock clock) {
        this.userFeignClient = userFeignClient;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.authSessionService = authSessionService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.accessTokenExpiration = accessTokenExpiration;
        this.clock = clock;
    }

    /** Compatibility constructor for isolated service tests which do not exercise online tracking. */
    public AuthServiceImpl(UserFeignClient userFeignClient, JwtProvider jwtProvider,
                           RefreshTokenService refreshTokenService, AuthSessionService authSessionService,
                           long accessTokenExpiration) {
        this(userFeignClient, jwtProvider, refreshTokenService, authSessionService, null, null,
                accessTokenExpiration, Clock.systemUTC());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        ValidateRequest validateRequest = new ValidateRequest(request.account(), request.password());
        ApiResponse<UserDTO> response = userFeignClient.validateUser(validateRequest);

        if (!response.success() || response.data() == null) {
            throw new BusinessException("AUTH_FAILED", "账号或密码错误");
        }

        UserDTO user = response.data();
        // SEC-03：签发可撤销会话，令牌携带 sid + 当前认证状态版本
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.USER, user.id());
        String accessToken = jwtProvider.generateUserAccessToken(
                new TokenPrincipal(SubjectType.USER, user.id(), session.sid(), session.authVersion()));
        // SEC-02：刷新家族绑定会话，"退出当前设备"只撤销本设备
        String refreshToken = refreshTokenService.createRefreshToken(SubjectType.USER, user.id(), session.sid());

        storeOnlineUser(session.sid(), user, httpRequest);

        return new LoginResponse(accessToken, refreshToken, "Bearer", accessTokenExpiration, null);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        // SEC-02：仅接受用户域（u:）令牌；原子轮换在家族内推进，不再整发新家族
        //（杜绝持续轮换无限续期）；重放/跨域错误由 RefreshTokenService 抛出稳定错误码。
        RotationResult rotation =
                refreshTokenService.rotateRefreshToken(SubjectType.USER, request.refreshToken());

        // SEC-03：刷新签发新会话并携带最新认证状态版本（权限软失效后自动拿到新版本）
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.USER, rotation.subjectId());
        String accessToken = jwtProvider.generateUserAccessToken(
                new TokenPrincipal(SubjectType.USER, rotation.subjectId(), session.sid(), session.authVersion()));

        // SEC-02：家族重绑到新会话并撤销旧会话——同一刷新家族同一时刻只有一个活动 sid，
        // 旧访问令牌随旧会话删除而立即失效（不无限新增活动会话）
        String previousSid = refreshTokenService.bindFamilySession(rotation.tokenValue(), session.sid());
        if (previousSid != null) {
            moveOnlineUser(previousSid, session.sid());
            authSessionService.revokeSession(previousSid);
        }

        return new LoginResponse(accessToken, rotation.tokenValue(), "Bearer", accessTokenExpiration, null);
    }

    @Override
    public void logout(Long userId, String sid) {
        // SEC-02：撤销当前会话 + 绑定该会话的刷新家族；其他设备不受影响。
        // sid 缺失（异常令牌）时保守撤销全部刷新家族——绝不伪造成已撤销的无操作。
        authSessionService.revokeSession(sid);
        if (sid == null || sid.isBlank()) {
            removeOnlineUsers(userId);
        } else {
            removeOnlineUser(sid);
        }
        if (sid == null || sid.isBlank()) {
            refreshTokenService.revokeAllTokensForSubject(SubjectType.USER, userId);
            return;
        }
        refreshTokenService.revokeFamilyBySession(SubjectType.USER, userId, sid);
    }

    @Override
    public void logoutAll(Long userId) {
        // SEC-02：版本递增使全部访问令牌秒级失效，撤销全部刷新家族阻断续期
        authSessionService.invalidate(SubjectType.USER, userId, true);
        removeOnlineUsers(userId);
    }

    private void storeOnlineUser(String sid, UserDTO user, HttpServletRequest request) {
        if (request == null || redisTemplate == null || objectMapper == null) {
            return;
        }
        String userAgent = request.getHeader("User-Agent");
        String ip = getRemoteIp(request);
        try {
            Map<String, String> info = Map.of(
                    "userId", String.valueOf(user.id()),
                    "username", nonBlank(user.username()),
                    "nickname", nonBlank(user.nickname()),
                    "ipaddr", nonBlank(ip),
                    "loginLocation", isPrivateIp(ip) ? "内网IP" : "未知",
                    "browser", parseBrowser(userAgent),
                    "os", parseOs(userAgent),
                    "loginTime", LocalDateTime.now(clock.withZone(BUSINESS_ZONE)).toString());
            redisTemplate.opsForValue().set(
                    SecurityConstants.USER_ONLINE_PREFIX + sid,
                    objectMapper.writeValueAsString(info),
                    Duration.ofSeconds(accessTokenExpiration));
        } catch (Exception e) {
            // Online tracking is best-effort and must not prevent a successful login.
            log.warn("Failed to store frontend online user: {}", e.getMessage());
        }
    }

    private void moveOnlineUser(String previousSid, String newSid) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String previousKey = SecurityConstants.USER_ONLINE_PREFIX + previousSid;
            String value = redisTemplate.opsForValue().get(previousKey);
            if (value == null) {
                return;
            }
            redisTemplate.opsForValue().set(SecurityConstants.USER_ONLINE_PREFIX + newSid, value,
                    Duration.ofSeconds(accessTokenExpiration));
            redisTemplate.delete(previousKey);
        } catch (Exception e) {
            log.warn("Failed to refresh frontend online user metadata: {}", e.getMessage());
        }
    }

    private void removeOnlineUser(String sid) {
        if (redisTemplate != null && sid != null && !sid.isBlank()) {
            try {
                redisTemplate.delete(SecurityConstants.USER_ONLINE_PREFIX + sid);
            } catch (Exception e) {
                log.warn("Failed to remove frontend online user metadata: {}", e.getMessage());
            }
        }
    }

    private void removeOnlineUsers(Long userId) {
        if (redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            var keys = redisTemplate.keys(SecurityConstants.USER_ONLINE_PREFIX + "*");
            if (keys == null) {
                return;
            }
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                try {
                    Map<?, ?> info = objectMapper.readValue(value, Map.class);
                    if (String.valueOf(userId).equals(String.valueOf(info.get("userId")))) {
                        redisTemplate.delete(key);
                    }
                } catch (Exception ignored) {
                    // Ignore malformed tracking metadata; the short TTL will remove it.
                }
            }
        } catch (Exception e) {
            log.warn("Failed to remove frontend online user metadata: {}", e.getMessage());
        }
    }

    private String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    private boolean isPrivateIp(String ip) {
        return ip != null && ("127.0.0.1".equals(ip) || "::1".equals(ip)
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && ip.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "未知";
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);
        if (ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/")) return "Edge";
        if (ua.contains("crios/")) return "Chrome (iOS)";
        if (ua.contains("chrome/")) return "Chrome";
        if (ua.contains("fxios/")) return "Firefox (iOS)";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("opr/") || ua.contains("opera/")) return "Opera";
        if (ua.contains("safari/") && !ua.contains("chrome/") && !ua.contains("crios/")) return "Safari";
        if (ua.contains("msie") || ua.contains("trident/")) return "IE";
        return "未知";
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) return "未知";
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) return "iOS";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "Mac OS";
        if (ua.contains("linux")) return "Linux";
        return "未知";
    }
}
