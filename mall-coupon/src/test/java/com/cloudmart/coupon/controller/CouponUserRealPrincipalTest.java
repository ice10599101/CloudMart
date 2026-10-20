package com.cloudmart.coupon.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.common.security.CloudmartSecurityProperties;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponRecommendationService;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.vo.UserCouponVO;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T23：用真实 RS256 JWT 过滤器（与生产同一实现）建立主体，验证 T03 的券归属
 * 只认已验签主体——外部自填的 X-User-Id 无法读取他人券；内部核销端点也拒绝
 * 用户/管理员令牌。
 */
@DisplayName("T03/T23 用户券真实主体归属")
class CouponUserRealPrincipalTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private AnnotationConfigApplicationContext context;
    private MockMvc mockMvc;
    private RSASSASigner signer;

    private final CouponService couponService = mock(CouponService.class);
    private final CouponRecommendationService couponRecommendationService = mock(CouponRecommendationService.class);
    private final ExchangeCodeService exchangeCodeService = mock(ExchangeCodeService.class);
    private final UserCouponConverter userCouponConverter = mock(UserCouponConverter.class);

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-kid")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        signer = new RSASSASigner(rsaKey);

        CloudmartSecurityProperties properties = new CloudmartSecurityProperties();
        properties.setServiceId("mall-coupon");
        UserJwtAuthenticationFilter jwtFilter = new UserJwtAuthenticationFilter(
                properties, jwkSource, Clock.fixed(NOW, ZoneOffset.UTC), null);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(MethodSecurityConfig.class);
        context.registerBean(CouponService.class, () -> couponService);
        context.registerBean(CouponRecommendationService.class, () -> couponRecommendationService);
        context.registerBean(ExchangeCodeService.class, () -> exchangeCodeService);
        context.registerBean(UserCouponConverter.class, () -> userCouponConverter);
        context.registerBean(CouponUserController.class);
        context.refresh();

        mockMvc = MockMvcBuilders.standaloneSetup(context.getBean(CouponUserController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtFilter)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private String token(String subject, String scope) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth")
                .audience(List.of("cloudmart-api"))
                .subject(subject)
                .claim("scope", scope)
                .claim("subjectType", scope)
                .claim("sid", "session-" + subject)
                .claim("authVersion", 0L)
                .issueTime(Date.from(NOW.minusSeconds(1)))
                .notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("用户自填他人 X-User-Id：仍按令牌主体查询，他人券 404")
    void spoofedUserIdHeaderCannotReadOthersCoupon() throws Exception {
        given(couponService.getUserCouponByIdAndUser(11L, 9001L))
                .willThrow(new BusinessException("USER_COUPON_NOT_FOUND", "用户优惠券不存在"));

        mockMvc.perform(get("/user-coupons/11")
                        .header("Authorization", "Bearer " + token("9001", "user"))
                        .header("X-User-Id", "9002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_COUPON_NOT_FOUND"));

        verify(couponService).getUserCouponByIdAndUser(11L, 9001L);
        verify(couponService, never()).getUserCouponByIdAndUser(11L, 9002L);
    }

    @Test
    @DisplayName("本人券：自填任意 X-User-Id 仍按令牌主体返回")
    void ownCouponResolvedByTokenSubject() throws Exception {
        UserCouponDTO dto = new UserCouponDTO(11L, 9001L, 10L, "UNUSED", null,
                NOW.atZone(ZoneOffset.UTC).toLocalDateTime(), null,
                NOW.plusSeconds(86400).atZone(ZoneOffset.UTC).toLocalDateTime(),
                "满100减20", "AMOUNT_OFF", new BigDecimal("100.00"), new BigDecimal("20.00"), null);
        given(couponService.getUserCouponByIdAndUser(11L, 9001L)).willReturn(dto);
        given(userCouponConverter.dtoToVO(dto)).willReturn(new UserCouponVO(11L, 9001L, 10L,
                "满100减20", "AMOUNT_OFF", new BigDecimal("20.00"), null, new BigDecimal("100.00"),
                "UNUSED", null, null, null, null));

        mockMvc.perform(get("/user-coupons/11")
                        .header("Authorization", "Bearer " + token("9001", "user"))
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));

        verify(couponService).getUserCouponByIdAndUser(11L, 9001L);
    }

    @Test
    @DisplayName("用户 JWT 调用核销端点 → 403（INTERNAL 仅服务令牌）")
    void userTokenCannotUseCoupon() throws Exception {
        mockMvc.perform(post("/user-coupons/use")
                        .header("Authorization", "Bearer " + token("9001", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCouponId\":11,\"orderId\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(couponService);
    }

    @Test
    @DisplayName("管理员 JWT 调用核销/退券端点 → 403（管理员不是内部服务主体）")
    void adminTokenCannotUseOrReturnCoupon() throws Exception {
        mockMvc.perform(post("/user-coupons/use")
                        .header("Authorization", "Bearer " + token("7", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCouponId\":11,\"orderId\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/user-coupons/return")
                        .header("Authorization", "Bearer " + token("7", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCouponId\":11,\"orderId\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(couponService);
    }
}
