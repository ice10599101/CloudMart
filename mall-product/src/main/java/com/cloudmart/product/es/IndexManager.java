package com.cloudmart.product.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * ES 索引管理器：负责索引的创建、删除、重建与状态检查。
 *
 * <p>通过读取 classpath:es/ 下的 JSON 文件定义索引 settings 与 mapping，
 * 替代 Spring Data Elasticsearch 的自动创建机制，确保 IK 分词器等自定义配置生效。</p>
 *
 * <p>仅当 {@code elasticsearch.enabled=true} 时启用。</p>
 */
@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class IndexManager {

    private static final Logger log = LoggerFactory.getLogger(IndexManager.class);
    private static final String INDEX_NAME = "products";
    private static final String SETTINGS_PATH = "es/products-settings.json";
    private static final String MAPPING_PATH = "es/products-mapping.json";

    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;

    public IndexManager(ElasticsearchOperations operations) {
        this.operations = operations;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 检查 products 索引是否存在。
     */
    public boolean indexExists() {
        return operations.indexOps(ProductDocument.class).exists();
    }

    /**
     * 创建 products 索引（含 settings + mapping）。
     * 若索引已存在则跳过。
     *
     * @return true 表示已创建或已存在
     */
    public boolean createIndexIfAbsent() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);

        if (indexOps.exists()) {
            log.info("ES index [{}] already exists, skip creation", INDEX_NAME);
            return true;
        }

        return createIndex(indexOps);
    }

    /**
     * 强制创建索引：先删除再创建。用于 mapping 变更场景。
     *
     * @return true 表示创建成功
     */
    public boolean recreateIndex() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        if (indexOps.exists()) {
            log.info("Deleting existing ES index [{}] for recreation", INDEX_NAME);
            indexOps.delete();
        }
        return createIndex(indexOps);
    }

    /**
     * 删除 products 索引。
     *
     * @return true 表示删除成功或本就不存在
     */
    public boolean deleteIndex() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        if (!indexOps.exists()) {
            log.info("ES index [{}] does not exist, skip deletion", INDEX_NAME);
            return true;
        }
        boolean deleted = indexOps.delete();
        log.info("ES index [{}] deleted: {}", INDEX_NAME, deleted);
        return deleted;
    }

    /**
     * 获取当前索引 mapping 信息（用于诊断）。
     */
    public Map<String, Object> getIndexMapping() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        Map<String, Object> mapping = indexOps.getMapping();
        return mapping != null ? mapping : Map.of();
    }

    /**
     * 获取当前索引 settings 信息（用于诊断）。
     */
    public Map<String, Object> getIndexSettings() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        org.springframework.data.elasticsearch.core.index.Settings settings = indexOps.getSettings();
        return settings != null ? settings : Map.of();
    }

    private boolean createIndex(IndexOperations indexOps) {
        try {
            Document settings = loadJsonAsDocument(SETTINGS_PATH);
            boolean created = indexOps.create(settings);
            if (!created) {
                log.error("Failed to create ES index [{}]", INDEX_NAME);
                return false;
            }

            Document mapping = loadJsonAsDocument(MAPPING_PATH);
            indexOps.putMapping(mapping);

            log.info("ES index [{}] created with custom settings and mapping", INDEX_NAME);
            return true;
        } catch (IOException e) {
            log.error("Failed to load index definition files for [{}]", INDEX_NAME, e);
            return false;
        }
    }

    private Document loadJsonAsDocument(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            String key = root.fieldNames().next();
            JsonNode inner = root.get(key);
            return Document.parse(objectMapper.writeValueAsString(inner));
        }
    }
}
