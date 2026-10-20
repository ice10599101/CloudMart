package com.cloudmart.file.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.common.security.CloudmartSecurityProperties;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.service.FileAssetService;
import com.cloudmart.file.service.FileDeletionService;
import com.cloudmart.file.service.FileService;
import com.cloudmart.file.service.UploadQuotaService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T23：T04 的真实主体回归——用生产同款 RS256 JWT 过滤器建立身份并强制改写
 * X-User-Id/X-Admin-Role；自填管理员头在用户令牌下不产生任何管理语义。
 */
@DisplayName("T04/T23 文件管理真实主体与伪造头")
class FileControllerRealPrincipalTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private MockMvc mockMvc;
    private RSASSASigner signer;

    private final FileService fileService = mock(FileService.class);
    private final UploadQuotaService uploadQuotaService = mock(UploadQuotaService.class);
    private final FileAssetService fileAssetService = mock(FileAssetService.class);
    private final FileDeletionService fileDeletionService = mock(FileDeletionService.class);

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
        properties.setServiceId("mall-file");
        UserJwtAuthenticationFilter jwtFilter = new UserJwtAuthenticationFilter(
                properties, jwkSource, Clock.fixed(NOW, ZoneOffset.UTC), null);

        given(fileAssetService.sha256Hex(any())).willReturn("a".repeat(64));
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, uploadQuotaService,
                        fileAssetService, fileDeletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtFilter)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
    }

    private FileAsset asset(long id) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setStorageKey("public/pic/20261020/test" + id + ".jpg");
        asset.setMime("image/jpeg");
        asset.setSizeBytes(13L);
        asset.setVisibility("PUBLIC");
        asset.setStatus("READY");
        return asset;
    }

    @Test
    @DisplayName("用户令牌 + 伪造 X-Admin-Role：上传仍是用户域（占配额、USER 归属、不要求 purpose）")
    void userTokenWithForgedAdminHeaderUploadsAsUser() throws Exception {
        given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
        given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261020/u.jpg");
        given(fileService.publicUrlOf(anyString())).willReturn("/files/pic/20261020/u.jpg");
        given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString())).willReturn(asset(60L));

        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .header("Authorization", "Bearer " + token("10001", "user"))
                        .header("X-User-Id", "9001")
                        .header("X-Admin-Role", "admin"))
                .andExpect(status().isOk());

        verify(uploadQuotaService).reserve("10001", "test.jpg");
        verify(fileAssetService).persist(eq(10001L), eq("USER"), eq(10001L), any(),
                anyString(), anyString(), anyLong(), anyString(), anyString(), eq("PUBLIC"));
    }

    @Test
    @DisplayName("用户令牌 + 伪造 X-Admin-Role：删除按非管理员、令牌主体裁决")
    void userTokenWithForgedAdminHeaderDeletesAsNonAdmin() throws Exception {
        mockMvc.perform(delete("/assets/5")
                        .header("Authorization", "Bearer " + token("10001", "user"))
                        .header("X-User-Id", "9001")
                        .header("X-Admin-Role", "admin"))
                .andExpect(status().isOk());

        verify(fileDeletionService).delete(5L, 10001L, false);
    }

    @Test
    @DisplayName("用户令牌 + 伪造 X-Admin-Role：私有下载授权不得按管理员签发")
    void userTokenWithForgedAdminHeaderCannotGetAdminDownload() throws Exception {
        given(fileAssetService.buildDownloadPath(eq(5L), eq(10001L), eq(false), any()))
                .willReturn("/files/assets/5/download?token=x&expires=1");

        mockMvc.perform(get("/assets/5/download-url")
                        .header("Authorization", "Bearer " + token("10001", "user"))
                        .header("X-Admin-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadPath").value("/files/assets/5/download?token=x&expires=1"));

        verify(fileAssetService, never()).buildDownloadPath(eq(5L), any(), eq(true), any());
    }

    @Test
    @DisplayName("管理员令牌：管理员语义来自验签角色，后台上传仍必须声明 purpose")
    void adminTokenMustDeclarePurpose() throws Exception {
        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .header("Authorization", "Bearer " + token("7", "admin"))
                        .header("X-User-Id", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_PURPOSE_REQUIRED"));

        verify(uploadQuotaService, never()).reserve(anyString(), anyString());
        verify(fileService, never()).store(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("管理员令牌：删除按验证后的管理员主体与角色放行")
    void adminTokenDeletesAsAdmin() throws Exception {
        mockMvc.perform(delete("/assets/5")
                        .header("Authorization", "Bearer " + token("7", "admin"))
                        .header("X-User-Id", "10001"))
                .andExpect(status().isOk());

        verify(fileDeletionService).delete(5L, 7L, true);
    }
}
