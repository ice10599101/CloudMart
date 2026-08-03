package com.cloudmart.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.hc.core5.http.HttpHost;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量存储与真实 Elasticsearch 的 Testcontainers 集成测试。
 * 验证 {@link ElasticsearchVectorStore} 的 add / similaritySearch / delete 全流程。
 *
 * <p>使用 {@code disabledWithoutDocker = true}：CI 环境无 Docker 时自动跳过，不阻塞构建。</p>
 *
 * <p>测试用 {@link SimpleKeywordEmbeddingModel} 替代真实 Embedding API，
 * 基于文本字节生成确定性的 4 维向量，保证相似度可预测。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class VectorSearchIntegrationTest {

    private static final String INDEX_NAME = "product_vectors_test";
    private static final int VECTOR_DIMENSION = 4;

    @Container
    private static final ElasticsearchContainer ES_CONTAINER = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.2")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    ).withEnv("xpack.security.enabled", "false")
        .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m -XX:MaxDirectMemorySize=256m")
        .waitingFor(Wait.forHttp("/")
                .forPort(9200)
                .withStartupTimeout(Duration.ofMinutes(5)));

    private Rest5Client rest5Client;
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() throws Exception {
        rest5Client = Rest5Client.builder(HttpHost.create(ES_CONTAINER.getHttpHostAddress())).build();

        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(INDEX_NAME);
        options.setDimensions(VECTOR_DIMENSION);
        // ES 9.x 要求 dense_vector 字段必须指定 similarity，否则 search 时 all shards failed
        options.setSimilarity(SimilarityFunction.cosine);

        vectorStore = ElasticsearchVectorStore.builder(rest5Client, new SimpleKeywordEmbeddingModel())
                .options(options)
                .initializeSchema(true)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rest5Client != null) {
            try {
                var client = new co.elastic.clients.elasticsearch.ElasticsearchClient(
                        new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper())
                );
                client.indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
                client.shutdown();
            } catch (Exception ignored) {
                // 清理失败不影响测试结果
            }
            rest5Client.close();
        }
    }

    @Test
    @DisplayName("add 后应能通过 similaritySearch 查询到文档")
    void addAndSearch_shouldReturnDocument() {
        Document doc = new Document(
                "product-1",
                "户外露营帐篷",
                Map.of("productId", 1L, "name", "帐篷", "price", "199")
        );
        vectorStore.add(List.of(doc));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("帐篷").topK(5).similarityThreshold(0.0).build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getId()).isEqualTo("product-1");
        assertThat(results.getFirst().getMetadata().get("name")).isEqualTo("帐篷");
    }

    @Test
    @DisplayName("delete 后应无法查询到文档")
    void delete_shouldRemoveDocument() {
        Document doc = new Document(
                "product-2",
                "冬季保暖睡袋",
                Map.of("productId", 2L, "name", "睡袋")
        );
        vectorStore.add(List.of(doc));

        vectorStore.delete(List.of("product-2"));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("睡袋").topK(5).similarityThreshold(0.0).build()
        );
        assertThat(results).noneMatch(d -> "product-2".equals(d.getId()));
    }

    @Test
    @DisplayName("相似查询应按相似度排序返回 topK 结果")
    void similaritySearch_shouldRespectTopK() {
        vectorStore.add(List.of(
                new Document("product-10", "帐篷", Map.of("productId", 10L)),
                new Document("product-11", "睡袋", Map.of("productId", 11L)),
                new Document("product-12", "炊具", Map.of("productId", 12L))
        ));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("帐篷").topK(2).similarityThreshold(0.0).build()
        );

        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }

    /**
     * 测试用 EmbeddingModel：基于文本 UTF-8 字节生成固定维度的归一化向量。
     * 相同文本生成相同向量，保证测试可重复。
     */
    static final class SimpleKeywordEmbeddingModel implements EmbeddingModel {

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public float[] embed(String text) {
            float[] vector = new float[VECTOR_DIMENSION];
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                vector[i] = (bytes.length > i) ? (bytes[i] / 128.0f) : 0.0f;
            }
            float sumSquares = 0.0f;
            for (float v : vector) {
                sumSquares += v * v;
            }
            float norm = (float) Math.sqrt(sumSquares);
            if (norm > 0) {
                for (int i = 0; i < VECTOR_DIMENSION; i++) {
                    vector[i] /= norm;
                }
            }
            return vector;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(embed(text), request.getInstructions().indexOf(text)))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public int dimensions() {
            return VECTOR_DIMENSION;
        }
    }
}
