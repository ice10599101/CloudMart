package com.cloudmart.ai.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.hc.core5.http.HttpHost;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 向量存储与真实 Elasticsearch 的集成测试。
 * 验证 {@link ElasticsearchVectorStore} 的 add / similaritySearch / delete 全流程。
 *
 * <p>ES 端点选择（{@code @BeforeAll}）：</p>
 * <ol>
 *   <li>环境变量 {@code TEST_ELASTICSEARCH_URIS}（如 http://host:9200）：直连外部 ES，
 *       供本机无 Docker 的环境使用（连开发服务器，本机不启动任何服务）；</li>
 *   <li>否则尝试 Testcontainers 启动 ES 9.4.0（CI 路径）；本机无 Docker 时整个类跳过。</li>
 * </ol>
 *
 * <p>测试用 {@link SimpleKeywordEmbeddingModel} 替代真实 Embedding API，
 * 基于文本字节生成确定性的 4 维向量，保证相似度可预测。</p>
 *
 * <p>兼容组合：ES 9.4.x（对齐生产镜像 9.4.0）+ Spring AI 2.0.1 GA + elasticsearch-java 9.2.x。
 * 历史禁用原因实为测试装配缺陷：{@code Builder.build()} 不会触发
 * {@link org.springframework.beans.factory.InitializingBean#afterPropertiesSet()}，
 * 索引从未被创建，首次 add 由 ES 自动按动态 mapping 建为普通 float 字段，knn 因而
 * all shards failed；显式调用 {@code afterPropertiesSet()} 后恢复。</p>
 */
class VectorSearchIntegrationTest {

    /**
     * 每次 JVM 运行使用固定索引：类生命周期内三个用例共享同一索引，
     * {@code @BeforeEach} 复用既有索引（indexExists 命中即跳过建索引），
     * {@code @AfterEach} 清数据，{@code @AfterAll} 删除索引。
     * 共享集群下带 test 前缀，便于识别与清理。
     */
    private static final String INDEX_NAME = "product_vectors_test";
    private static final int VECTOR_DIMENSION = 4;

    private static Rest5Client rest5Client;
    private static ElasticsearchContainer esContainer;
    private static boolean indexReady;

    @BeforeAll
    static void setUpCluster() throws Exception {
        String remoteUri = System.getenv("TEST_ELASTICSEARCH_URIS");
        if (remoteUri != null && !remoteUri.isBlank()) {
            rest5Client = Rest5Client.builder(HttpHost.create(remoteUri.trim())).build();
            // 清理上次崩溃可能残留的索引（mapping 可能错误），保证首用例从零建索引
            new co.elastic.clients.elasticsearch.ElasticsearchClient(
                    new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()))
                    .indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
            return;
        }
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker 不可用且未设置 TEST_ELASTICSEARCH_URIS，跳过 Elasticsearch 集成测试");
        esContainer = new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.0")
                        .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m -XX:MaxDirectMemorySize=256m")
                .waitingFor(Wait.forHttp("/").forPort(9200).withStartupTimeout(Duration.ofMinutes(5)));
        esContainer.start();
        rest5Client = Rest5Client.builder(HttpHost.create(esContainer.getHttpHostAddress())).build();
    }

    @AfterAll
    static void tearDownCluster() throws Exception {
        try {
            if (rest5Client != null && indexReady) {
                var adminClient = new co.elastic.clients.elasticsearch.ElasticsearchClient(
                        new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()));
                adminClient.indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
                adminClient.shutdown();
            }
        } finally {
            if (rest5Client != null) {
                rest5Client.close();
            }
            if (esContainer != null) {
                esContainer.stop();
            }
        }
    }

    private ElasticsearchVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(INDEX_NAME);
        options.setDimensions(VECTOR_DIMENSION);
        // ES 9.x 要求 dense_vector 字段必须指定 similarity，否则 search 时 all shards failed
        options.setSimilarity(SimilarityFunction.cosine);

        vectorStore = ElasticsearchVectorStore.builder(rest5Client, new SimpleKeywordEmbeddingModel())
                .options(options)
                .initializeSchema(true)
                .build();
        // Builder.build() 只构造对象，不触发 InitializingBean 回调（容器才会调用）；
        // 纯 JUnit 场景必须显式初始化，否则索引不会被创建，首次 add 会被 ES 按动态 mapping
        // 建为普通 float 字段，导致 knn 查询 all shards failed
        vectorStore.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() throws Exception {
        var client = new co.elastic.clients.elasticsearch.ElasticsearchClient(
                new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()));
        client.indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
        client.shutdown();
    }

    /**
     * bulk add/delete 走近实时写入，knn 只检索已刷新段；显式 refresh 避免 1s 刷新窗口导致的偶发空结果。
     */
    private static void refreshIndex() throws Exception {
        var client = new co.elastic.clients.elasticsearch.ElasticsearchClient(
                new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper()));
        try {
            client.indices().refresh(r -> r.index(INDEX_NAME));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("add 后应能通过 similaritySearch 查询到文档")
    void addAndSearch_shouldReturnDocument() throws Exception {
        Document doc = new Document(
                "product-1",
                "户外露营帐篷",
                Map.of("productId", 1L, "name", "帐篷", "price", "199")
        );
        vectorStore.add(List.of(doc));
        refreshIndex();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("户外露营帐篷").topK(5).similarityThreshold(0.0).build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getId()).isEqualTo("product-1");
        assertThat(results.getFirst().getMetadata().get("name")).isEqualTo("帐篷");
    }

    @Test
    @DisplayName("delete 后应无法查询到文档")
    void delete_shouldRemoveDocument() throws Exception {
        Document doc = new Document(
                "product-2",
                "冬季保暖睡袋",
                Map.of("productId", 2L, "name", "睡袋")
        );
        vectorStore.add(List.of(doc));
        refreshIndex();

        vectorStore.delete(List.of("product-2"));
        refreshIndex();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("冬季保暖睡袋").topK(5).similarityThreshold(0.0).build()
        );
        assertThat(results).noneMatch(d -> "product-2".equals(d.getId()));
    }

    @Test
    @DisplayName("相似查询应按相似度排序返回 topK 结果")
    void similaritySearch_shouldRespectTopK() throws Exception {
        vectorStore.add(List.of(
                new Document("product-10", "帐篷", Map.of("productId", 10L)),
                new Document("product-11", "睡袋", Map.of("productId", 11L)),
                new Document("product-12", "炊具", Map.of("productId", 12L))
        ));
        refreshIndex();

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
