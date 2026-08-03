package com.cloudmart.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.cloudmart.payment.config.AlipayConfig;
import com.cloudmart.payment.dto.PaymentDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlipayPaymentServiceImpl 单元测试")
class AlipayPaymentServiceImplTest {

    @Mock
    private AlipayClient alipayClient;

    @Mock
    private AlipayConfig alipayConfig;

    @InjectMocks
    private AlipayPaymentServiceImpl alipayPaymentService;

    private PaymentDTO buildPaymentDTO() {
        return new PaymentDTO(
            1L, 100L, "PAY-20260531001",
            new BigDecimal("199.90"), "ALIPAY", "PENDING",
            null, LocalDateTime.now(), null
        );
    }

    @Nested
    @DisplayName("createTrade 方法")
    class CreateTradeTests {

        @Test
        @DisplayName("创建交易 - 成功返回支付表单HTML")
        void shouldReturnHtmlBodyOnSuccess() throws AlipayApiException {
            PaymentDTO payment = buildPaymentDTO();
            AlipayTradePagePayResponse response = org.mockito.Mockito.mock();

            when(alipayConfig.getNotifyUrl()).thenReturn("https://notify.example.com");
            when(alipayConfig.getReturnUrl()).thenReturn("https://return.example.com");
            when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(response);
            when(response.getBody()).thenReturn("<form>alipay</form>");

            String result = alipayPaymentService.createTrade(payment);

            assertThat(result).isEqualTo("<form>alipay</form>");
        }

        @Test
        @DisplayName("创建交易 - 设置回调URL")
        void shouldSetCallbackUrls() throws AlipayApiException {
            PaymentDTO payment = buildPaymentDTO();
            AlipayTradePagePayResponse response = org.mockito.Mockito.mock();

            when(alipayConfig.getNotifyUrl()).thenReturn("https://notify.example.com");
            when(alipayConfig.getReturnUrl()).thenReturn("https://return.example.com");
            when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(response);
            when(response.getBody()).thenReturn("html");

            alipayPaymentService.createTrade(payment);

            verify(alipayConfig).getNotifyUrl();
            verify(alipayConfig).getReturnUrl();
        }

        @Test
        @DisplayName("创建交易 - API异常时抛出RuntimeException")
        void shouldThrowRuntimeExceptionOnApiException() throws AlipayApiException {
            PaymentDTO payment = buildPaymentDTO();

            when(alipayConfig.getNotifyUrl()).thenReturn("https://notify.example.com");
            when(alipayConfig.getReturnUrl()).thenReturn("https://return.example.com");
            when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class)))
                .thenThrow(new AlipayApiException("api error"));

            assertThatThrownBy(() -> alipayPaymentService.createTrade(payment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("支付宝下单失败");
        }
    }

    @Nested
    @DisplayName("verifyCallback 方法")
    class VerifyCallbackTests {

        @Test
        @DisplayName("验签 - 签名验证通过返回true")
        void shouldReturnTrueWhenSignatureValid() {
            Map<String, String> params = Map.of(
                "out_trade_no", "PAY-001",
                "trade_status", "TRADE_SUCCESS",
                "sign", "mock_sign"
            );

            when(alipayConfig.getAlipayPublicKey()).thenReturn("mock_public_key");

            try (MockedStatic<AlipaySignature> signatureMock = mockStatic(AlipaySignature.class)) {
                signatureMock.when(() ->
                    AlipaySignature.rsaCheckV1(params, "mock_public_key", "UTF-8", "RSA2")
                ).thenReturn(true);

                boolean result = alipayPaymentService.verifyCallback(params);

                assertThat(result).isTrue();
            }
        }

        @Test
        @DisplayName("验签 - 签名验证失败返回false")
        void shouldReturnFalseWhenSignatureInvalid() {
            Map<String, String> params = Map.of(
                "out_trade_no", "PAY-001",
                "trade_status", "TRADE_SUCCESS",
                "sign", "invalid_sign"
            );

            when(alipayConfig.getAlipayPublicKey()).thenReturn("mock_public_key");

            try (MockedStatic<AlipaySignature> signatureMock = mockStatic(AlipaySignature.class)) {
                signatureMock.when(() ->
                    AlipaySignature.rsaCheckV1(params, "mock_public_key", "UTF-8", "RSA2")
                ).thenReturn(false);

                boolean result = alipayPaymentService.verifyCallback(params);

                assertThat(result).isFalse();
            }
        }

        @Test
        @DisplayName("验签 - API异常时返回false")
        void shouldReturnFalseOnApiException() {
            Map<String, String> params = Map.of(
                "out_trade_no", "PAY-001",
                "sign", "mock_sign"
            );

            when(alipayConfig.getAlipayPublicKey()).thenReturn("mock_public_key");

            try (MockedStatic<AlipaySignature> signatureMock = mockStatic(AlipaySignature.class)) {
                signatureMock.when(() ->
                    AlipaySignature.rsaCheckV1(params, "mock_public_key", "UTF-8", "RSA2")
                ).thenThrow(new AlipayApiException("verify error"));

                boolean result = alipayPaymentService.verifyCallback(params);

                assertThat(result).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("refund 方法")
    class RefundTests {

        @Test
        @DisplayName("退款 - 成功返回true")
        void shouldReturnTrueOnRefundSuccess() throws AlipayApiException {
            AlipayTradeRefundResponse response = org.mockito.Mockito.mock();
            when(response.isSuccess()).thenReturn(true);
            when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);

            boolean result = alipayPaymentService.refund(1L, "商品质量问题");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("退款 - 退款失败返回false")
        void shouldReturnFalseOnRefundFailure() throws AlipayApiException {
            AlipayTradeRefundResponse response = org.mockito.Mockito.mock();
            when(response.isSuccess()).thenReturn(false);
            when(response.getSubMsg()).thenReturn("REFUND_NOT_FOUND");
            when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);

            boolean result = alipayPaymentService.refund(1L, "商品质量问题");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("退款 - API异常时返回false")
        void shouldReturnFalseOnApiException() throws AlipayApiException {
            when(alipayClient.execute(any(AlipayTradeRefundRequest.class)))
                .thenThrow(new AlipayApiException("network error"));

            boolean result = alipayPaymentService.refund(1L, "商品质量问题");

            assertThat(result).isFalse();
        }
    }
}
