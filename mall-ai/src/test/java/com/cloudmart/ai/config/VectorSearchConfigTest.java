package com.cloudmart.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VectorSearchConfig} 配置类单元测试：验证条件装配注解元数据。
 *
 * <p>配置类使用 {@code @ConditionalOnProperty("ai.vector.enabled")} 和
 * {@code @ConditionalOnBean(Rest5Client.class)} 守护，确保向量存储仅在显式启用且
 * ES 客户端可用时才创建。</p>
 */
class VectorSearchConfigTest {

    @Nested
    @DisplayName("配置类注解")
    class ConfigurationAnnotations {

        @Test
        @DisplayName("VectorSearchConfig 应标注 @Configuration")
        void shouldBeAnnotatedWithConfiguration() {
            assertThat(VectorSearchConfig.class.isAnnotationPresent(Configuration.class))
                    .as("VectorSearchConfig 必须标注 @Configuration")
                    .isTrue();
        }

        @Test
        @DisplayName("VectorSearchConfig 应通过 @ConditionalOnProperty 守护 ai.vector.enabled")
        void shouldBeGuardedByConditionalOnProperty() {
            ConditionalOnProperty annotation = VectorSearchConfig.class.getAnnotation(ConditionalOnProperty.class);
            assertThat(annotation)
                    .as("VectorSearchConfig 必须标注 @ConditionalOnProperty")
                    .isNotNull();
            assertThat(annotation.name()).contains("ai.vector.enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
            assertThat(annotation.matchIfMissing()).isFalse();
        }
    }

    @Nested
    @DisplayName("vectorStore Bean 定义")
    class VectorStoreBeanDefinition {

        @Test
        @DisplayName("vectorStore 方法应标注 @Bean")
        void shouldBeAnnotatedWithBean() throws NoSuchMethodException {
            Method vectorStoreMethod = VectorSearchConfig.class.getDeclaredMethod(
                    "vectorStore",
                    co.elastic.clients.transport.rest5_client.low_level.Rest5Client.class,
                    EmbeddingModel.class,
                    String.class
            );

            assertThat(vectorStoreMethod.isAnnotationPresent(Bean.class))
                    .as("vectorStore 方法必须标注 @Bean")
                    .isTrue();
        }

        @Test
        @DisplayName("vectorStore 方法应通过 @ConditionalOnBean(Rest5Client) 守护")
        void shouldBeGuardedByConditionalOnBean() throws NoSuchMethodException {
            Method vectorStoreMethod = VectorSearchConfig.class.getDeclaredMethod(
                    "vectorStore",
                    co.elastic.clients.transport.rest5_client.low_level.Rest5Client.class,
                    EmbeddingModel.class,
                    String.class
            );

            ConditionalOnBean annotation = vectorStoreMethod.getAnnotation(ConditionalOnBean.class);
            assertThat(annotation)
                    .as("vectorStore 方法必须标注 @ConditionalOnBean")
                    .isNotNull();
            assertThat(annotation.value()).contains(co.elastic.clients.transport.rest5_client.low_level.Rest5Client.class);
        }

        @Test
        @DisplayName("vectorStore 方法应返回 VectorStore 类型")
        void shouldReturnVectorStoreType() throws NoSuchMethodException {
            Method vectorStoreMethod = VectorSearchConfig.class.getDeclaredMethod(
                    "vectorStore",
                    co.elastic.clients.transport.rest5_client.low_level.Rest5Client.class,
                    EmbeddingModel.class,
                    String.class
            );

            assertThat(VectorStore.class.isAssignableFrom(vectorStoreMethod.getReturnType()))
                    .as("vectorStore 方法返回类型必须是 VectorStore 或其子类型")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("索引名称配置")
    class IndexNameConfiguration {

        @Test
        @DisplayName("应使用 ai.vector.index-name 配置项，默认值为 cloudmart_product_vectors")
        void shouldUseConfigurableIndexNameWithDefault() throws NoSuchMethodException {
            Method vectorStoreMethod = VectorSearchConfig.class.getDeclaredMethod(
                    "vectorStore",
                    co.elastic.clients.transport.rest5_client.low_level.Rest5Client.class,
                    EmbeddingModel.class,
                    String.class
            );

            org.springframework.beans.factory.annotation.Value valueAnnotation =
                    vectorStoreMethod.getParameters()[2].getAnnotation(org.springframework.beans.factory.annotation.Value.class);

            assertThat(valueAnnotation)
                    .as("indexName 参数必须标注 @Value")
                    .isNotNull();
            assertThat(valueAnnotation.value())
                    .contains("ai.vector.index-name")
                    .contains("cloudmart_product_vectors");
        }
    }
}
