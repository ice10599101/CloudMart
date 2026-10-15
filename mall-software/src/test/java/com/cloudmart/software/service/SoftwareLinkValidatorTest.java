package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SoftwareSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 5.1 链接校验单测（A12 URL 攻击面）：网盘域名精确匹配、协议/host 攻击拒绝、
 * 磁力 BTIH 语法、指纹对提取码/密码敏感。
 */
class SoftwareLinkValidatorTest {

    private final SoftwareLinkValidator validator = new SoftwareLinkValidator();

    @Test
    @DisplayName("网盘域名映射：官方域与子域通过，provider 由服务端裁定")
    void netdisk_providerMapping() {
        var baidu = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://pan.baidu.com/s/1abc?pwd=xyz", "a1b2", null);
        assertThat(baidu.provider()).isEqualTo(SoftwareProvider.BAIDU);
        assertThat(baidu.extractionCode()).isEqualTo("a1b2");

        // 一级子域（开发者可配的自定义域）同样匹配
        var sub = validator.validate(SoftwareSourceType.NETDISK, "BAIDU",
                "https://test.pan.baidu.com/s/1abc", null, null);
        assertThat(sub.provider()).isEqualTo(SoftwareProvider.BAIDU);

        var quark = validator.validate(SoftwareSourceType.NETDISK, "quark",
                "https://pan.quark.cn/s/abc", null, "pw");
        assertThat(quark.provider()).isEqualTo(SoftwareProvider.QUARK);
        assertThat(quark.archivePassword()).isEqualTo("pw");
    }

    @Test
    @DisplayName("A12：evilbaidu.com 不因 endsWith 误匹配；客户端谎报 provider 被拒")
    void netdisk_hostExactMatch() {
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.NETDISK, null,
                "https://evilbaidu.com/s/1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_INVALID");

        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.NETDISK, "QUARK",
                "https://pan.baidu.com/s/1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_INVALID");
    }

    @Test
    @DisplayName("A12：非 https/userinfo/localhost/IP 字面量/控制字符一律拒绝")
    void https_attacksRejected() {
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.TORRENT_URL, null,
                "http://example.com/a.torrent", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.TORRENT_URL, null,
                "https://user:pass@example.com/a", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.TORRENT_URL, null,
                "https://localhost/a.torrent", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.TORRENT_URL, null,
                "https://192.168.1.1/a.torrent", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.TORRENT_URL, null,
                "https://example.com/\u0001", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("磁力：40 位十六进制与 32 位 Base32 通过；错误 BTIH/未知参数/双 xt 拒绝")
    void magnet_btihRules() {
        var hex = validator.validate(SoftwareSourceType.MAGNET, null,
                "magnet:?xt=urn:btih:" + "a".repeat(40) + "&dn=test&tr=https%3A%2F%2Ftracker.example.com%2Fannounce&xl=1024",
                null, null);
        assertThat(hex.provider()).isEqualTo(SoftwareProvider.OTHER);

        var base32 = validator.validate(SoftwareSourceType.MAGNET, null,
                "magnet:?xt=urn:btih:" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", null, null);
        assertThat(base32.urlHash()).isNotBlank();

        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.MAGNET, null,
                "magnet:?xt=urn:btih:short", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.MAGNET, null,
                "magnet:?xt=urn:btih:" + "a".repeat(40) + "&cmd=evil", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(SoftwareSourceType.MAGNET, null,
                "magnet:?xt=urn:btih:" + "a".repeat(40) + "&xt=urn:btih:" + "b".repeat(40), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("指纹：规范化 URL 稳定；提取码/解压密码变化即指纹变化（4.3 核实结论失效依据）")
    void fingerprint_sensitivity() {
        var base = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://Pan.Baidu.COM:443/s/1abc", "code1", "pwd1");
        // 标准化：host 小写、默认端口 443 去除
        assertThat(base.normalizedUrl()).isEqualTo("https://pan.baidu.com/s/1abc");

        var sameUrlDifferentCode = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://pan.baidu.com/s/1abc", "code2", "pwd1");
        var sameUrlDifferentPassword = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://pan.baidu.com/s/1abc", "code1", "pwd2");

        assertThat(base.contentFingerprint()).isNotEqualTo(sameUrlDifferentCode.contentFingerprint());
        assertThat(base.contentFingerprint()).isNotEqualTo(sameUrlDifferentPassword.contentFingerprint());
        // 同请求重放指纹一致
        var repeat = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://Pan.Baidu.COM:443/s/1abc", "code1", "pwd1");
        assertThat(base.contentFingerprint()).isEqualTo(repeat.contentFingerprint());
    }

    @Test
    @DisplayName("提取码/密码 trim 保留大小写（纯文本，不转大小写）")
    void secrets_trimPreservingCase() {
        var link = validator.validate(SoftwareSourceType.NETDISK, null,
                "https://pan.baidu.com/s/1", "  AbC  ", " PwD ");
        assertThat(link.extractionCode()).isEqualTo("AbC");
        assertThat(link.archivePassword()).isEqualTo("PwD");
    }
}
