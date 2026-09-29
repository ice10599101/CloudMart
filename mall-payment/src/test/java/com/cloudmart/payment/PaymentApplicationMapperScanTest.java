package com.cloudmart.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-01 回归：MapperScan 必须覆盖 reconciliation 包——对账 Mapper 在该包，
 * 漏扫会导致启动即挂（曾因此启动失败）。
 */
@DisplayName("PaymentApplication MapperScan 覆盖")
class PaymentApplicationMapperScanTest {

    @Test
    @DisplayName("@MapperScan 包含 reconciliation 包（对账 Mapper 注册回归）")
    void mapperScan_coversReconciliationPackage() {
        var annotation = PaymentApplication.class.getAnnotation(
                org.mybatis.spring.annotation.MapperScan.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(
                "com.cloudmart.payment.repository",
                "com.cloudmart.payment.reconciliation");
    }
}
