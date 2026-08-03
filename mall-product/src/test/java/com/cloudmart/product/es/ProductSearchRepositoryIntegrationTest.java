package com.cloudmart.product.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductDocument} 与 ES 索引交互的 Testcontainers 集成测试。
 * 启动真实 Elasticsearch 9.x 容器，验证文档序列化、CRUD 与查询语法。
 *
 * <p>使用 {@code disabledWithoutDocker = true}：CI 环境无 Docker 时自动跳过，不阻塞构建。</p>
 *
 * <p>注意：testcontainers 默认 ES 镜像不含 IK 分词器插件，因此本测试不验证
 * ik_max_word/ik_smart 分析器行为，仅验证字段映射、CRUD 与查询逻辑。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class ProductSearchRepositoryIntegrationTest {

    private static final String INDEX_NAME = "products_test";

    /**
     * Elasticsearch 9.x 容器，关闭 xpack security 简化测试连接。
     */
    @Container
    private static final ElasticsearchContainer ES_CONTAINER = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.2")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    ).withEnv("xpack.security.enabled", "false");

    private ElasticsearchClient client;

    @BeforeEach
    void setUp() throws Exception {
        Rest5Client rest5Client = Rest5Client.builder(HttpHost.create(ES_CONTAINER.getHttpHostAddress())).build();
        Rest5ClientTransport transport = new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);

        client.indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
        client.indices().create(CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("0")))
                .mappings(m -> m
                        .properties("id", p -> p.long_(b -> b))
                        .properties("name", p -> p.text(b -> b.analyzer("standard")))
                        .properties("description", p -> p.text(b -> b.analyzer("standard")))
                        .properties("categoryId", p -> p.long_(b -> b))
                        .properties("brand", p -> p.keyword(b -> b))
                        .properties("minPrice", p -> p.double_(b -> b))
                        .properties("maxOriginalPrice", p -> p.double_(b -> b))
                        .properties("mainImage", p -> p.keyword(b -> b))
                        .properties("createdAt", p -> p.date(b -> b))
                )
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.indices().delete(d -> d.index(INDEX_NAME).ignoreUnavailable(true));
            client.shutdown();
        }
    }

    private ProductDocument buildDoc(Long id, String name, Long categoryId, Double minPrice) {
        ProductDocument doc = new ProductDocument();
        doc.setId(id);
        doc.setName(name);
        doc.setDescription(name + " 描述");
        doc.setCategoryId(categoryId);
        doc.setBrand("测试品牌");
        doc.setMinPrice(minPrice);
        doc.setMaxOriginalPrice(minPrice != null ? minPrice * 1.2 : null);
        doc.setMainImage("http://img.test.com/" + id + ".jpg");
        doc.setCreatedAt(LocalDateTime.now());
        return doc;
    }

    @Test
    @DisplayName("写入文档后应能按 ID 精确查询")
    void indexAndGet_shouldRoundTrip() throws Exception {
        ProductDocument doc = buildDoc(1L, "帐篷", 100L, 199.0);

        client.index(i -> i.index(INDEX_NAME).id("1").document(doc));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        var response = client.get(g -> g.index(INDEX_NAME).id("1"), ProductDocument.class);

        assertThat(response.found()).isTrue();
        ProductDocument found = response.source();
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("帐篷");
        assertThat(found.getCategoryId()).isEqualTo(100L);
        assertThat(found.getMinPrice()).isEqualTo(199.0);
        assertThat(found.getBrand()).isEqualTo("测试品牌");
    }

    @Test
    @DisplayName("删除文档后应无法查询")
    void deleteDocument_shouldRemoveFromIndex() throws Exception {
        client.index(i -> i.index(INDEX_NAME).id("2").document(buildDoc(2L, "睡袋", 100L, 99.0)));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        client.delete(d -> d.index(INDEX_NAME).id("2"));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        var response = client.get(g -> g.index(INDEX_NAME).id("2"), ProductDocument.class);
        assertThat(response.found()).isFalse();
    }

    @Test
    @DisplayName("应能按 categoryId 精确过滤")
    void search_shouldFilterByCategoryId() throws Exception {
        client.index(i -> i.index(INDEX_NAME).id("10").document(buildDoc(10L, "户外帐篷", 100L, 199.0)));
        client.index(i -> i.index(INDEX_NAME).id("11").document(buildDoc(11L, "厨房刀具", 200L, 59.0)));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        SearchResponse<ProductDocument> response = client.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.term(t -> t.field("categoryId").value(100L))), ProductDocument.class);

        assertThat(response.hits().total().value()).isEqualTo(1L);
        assertThat(response.hits().hits().getFirst().source().getName()).isEqualTo("户外帐篷");
    }

    @Test
    @DisplayName("应能按 minPrice 范围过滤")
    void search_shouldFilterByPriceRange() throws Exception {
        client.index(i -> i.index(INDEX_NAME).id("20").document(buildDoc(20L, "低价商品", 100L, 29.0)));
        client.index(i -> i.index(INDEX_NAME).id("21").document(buildDoc(21L, "中价商品", 100L, 199.0)));
        client.index(i -> i.index(INDEX_NAME).id("22").document(buildDoc(22L, "高价商品", 100L, 999.0)));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        SearchResponse<ProductDocument> response = client.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.range(r -> r.untyped(u -> u
                        .field("minPrice")
                        .gte(co.elastic.clients.json.JsonData.of(100.0))
                        .lte(co.elastic.clients.json.JsonData.of(500.0))))), ProductDocument.class);

        assertThat(response.hits().total().value()).isEqualTo(1L);
        assertThat(response.hits().hits().getFirst().source().getName()).isEqualTo("中价商品");
    }

    @Test
    @DisplayName("应能按 name 全文检索")
    void search_shouldMatchByName() throws Exception {
        client.index(i -> i.index(INDEX_NAME).id("30").document(buildDoc(30L, "户外露营帐篷", 100L, 199.0)));
        client.index(i -> i.index(INDEX_NAME).id("31").document(buildDoc(31L, "厨房刀具", 200L, 59.0)));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        SearchResponse<ProductDocument> response = client.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.match(m -> m.field("name").query("帐篷"))), ProductDocument.class);

        assertThat(response.hits().total().value()).isEqualTo(1L);
        assertThat(response.hits().hits().getFirst().source().getName()).contains("帐篷");
    }

    @Test
    @DisplayName("应能按多个字段组合查询")
    void search_shouldCombineFilters() throws Exception {
        client.index(i -> i.index(INDEX_NAME).id("40").document(buildDoc(40L, "帐篷", 100L, 199.0)));
        client.index(i -> i.index(INDEX_NAME).id("41").document(buildDoc(41L, "睡袋", 100L, 99.0)));
        client.index(i -> i.index(INDEX_NAME).id("42").document(buildDoc(42L, "帐篷", 200L, 299.0)));
        client.indices().refresh(r -> r.index(INDEX_NAME));

        SearchResponse<ProductDocument> response = client.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.bool(b -> b
                        .must(m -> m.match(mm -> mm.field("name").query("帐篷")))
                        .filter(f -> f.term(t -> t.field("categoryId").value(100L))))), ProductDocument.class);

        assertThat(response.hits().total().value()).isEqualTo(1L);
        assertThat(response.hits().hits().getFirst().source().getId()).isEqualTo(40L);
    }
}
