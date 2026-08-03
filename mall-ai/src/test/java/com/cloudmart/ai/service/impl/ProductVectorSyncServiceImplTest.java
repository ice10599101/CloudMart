package com.cloudmart.ai.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVectorSyncServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    private ProductVectorSyncServiceImpl syncService;

    private static final Long PRODUCT_ID = 1L;

    @SuppressWarnings("unchecked")
    private void setupRestClientMock(Map<String, Object> responseBody) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object[].class));
        doReturn(headersSpec).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(responseBody).when(responseSpec).body(any(Class.class));

        ReflectionTestUtils.setField(syncService, "productRestClient", restClient);
    }

    private void setupRestClientException() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        doReturn(uriSpec).when(restClient).get();
        doThrow(new RuntimeException("Connection refused")).when(uriSpec).uri(anyString(), any(Object[].class));
        ReflectionTestUtils.setField(syncService, "productRestClient", restClient);
    }

    @BeforeEach
    void setUp() {
        syncService = new ProductVectorSyncServiceImpl(vectorStore, "http://mall-product");
    }

    @Nested
    @DisplayName("syncProduct")
    class SyncProductTests {

        @Test
        @DisplayName("should sync product to vector store when response is successful")
        void syncProduct_success_syncsToVectorStore() {
            Map<String, Object> productData = Map.of(
                    "id", PRODUCT_ID,
                    "name", "测试商品",
                    "description", "商品描述",
                    "price", "99.9",
                    "mainImage", "http://img.test.com/1.jpg",
                    "categoryName", "测试分类"
            );
            Map<String, Object> response = Map.of("success", true, "data", productData);
            setupRestClientMock(response);

            syncService.syncProduct(PRODUCT_ID);

            verify(vectorStore).add(any(List.class));
        }

        @Test
        @DisplayName("should not sync when response is null")
        void syncProduct_nullResponse_doesNotSync() {
            setupRestClientMock(null);

            syncService.syncProduct(PRODUCT_ID);

            verify(vectorStore, never()).add(any(List.class));
        }

        @Test
        @DisplayName("should not sync when response indicates failure")
        void syncProduct_failedResponse_doesNotSync() {
            Map<String, Object> response = Map.of("success", false);
            setupRestClientMock(response);

            syncService.syncProduct(PRODUCT_ID);

            verify(vectorStore, never()).add(any(List.class));
        }

        @Test
        @DisplayName("should handle RestClient exception gracefully")
        void syncProduct_exception_doesNotThrow() {
            setupRestClientException();

            assertThatCode(() -> syncService.syncProduct(PRODUCT_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not sync when response data is null")
        void syncProduct_nullData_doesNotSync() {
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("data", null);
            setupRestClientMock(response);

            syncService.syncProduct(PRODUCT_ID);

            verify(vectorStore, never()).add(any(List.class));
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProductTests {

        @Test
        @DisplayName("should delete product from vector store")
        void deleteProduct_success_deletesFromVectorStore() {
            syncService.deleteProduct(PRODUCT_ID);

            verify(vectorStore).delete(List.of("product-" + PRODUCT_ID));
        }

        @Test
        @DisplayName("should handle delete exception gracefully")
        void deleteProduct_exception_doesNotThrow() {
            doThrow(new RuntimeException("Delete failed")).when(vectorStore).delete(any(List.class));

            assertThatCode(() -> syncService.deleteProduct(PRODUCT_ID)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("fullSync")
    class FullSyncTests {

        @Test
        @DisplayName("should sync products in batches and stop when page is empty")
        void fullSync_withProducts_syncsAllBatches() {
            Map<String, Object> product1 = Map.of(
                    "id", 1, "name", "商品1", "description", "描述1",
                    "price", "99", "mainImage", "img1.jpg", "categoryName", "分类1"
            );
            Map<String, Object> product2 = Map.of(
                    "id", 2, "name", "商品2", "description", "描述2",
                    "price", "199", "mainImage", "img2.jpg", "categoryName", "分类2"
            );

            Map<String, Object> page0Data = Map.of("records", List.of(product1, product2));
            Map<String, Object> page0Response = Map.of("success", true, "data", page0Data);

            Map<String, Object> page1Data = Map.of("records", Collections.emptyList());
            Map<String, Object> page1Response = Map.of("success", true, "data", page1Data);

            RestClient restClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
            RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

            doReturn(uriSpec).when(restClient).get();
            doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object[].class));
            doReturn(headersSpec).when(headersSpec).header(anyString(), any(String[].class));
            doReturn(responseSpec).when(headersSpec).retrieve();
            doReturn(page0Response, page1Response).when(responseSpec).body(any(Class.class));

            ReflectionTestUtils.setField(syncService, "productRestClient", restClient);

            syncService.fullSync();

            verify(vectorStore).add(any(List.class));
        }

        @Test
        @DisplayName("should handle fetch exception gracefully during full sync")
        void fullSync_fetchException_stopsGracefully() {
            setupRestClientException();

            assertThatCode(() -> syncService.fullSync()).doesNotThrowAnyException();
        }
    }
}
