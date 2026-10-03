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
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.index.AliasData;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ES 索引管理器（T08 版本驱动投影）：实体索引版本化（products_v1、products_v2…），
 * 读写统一走别名 {@link #ALIAS_NAME}，索引结构重建通过"建新版本 → 写入新版本 →
 * 原子切换别名"实现零停机，旧版本保留可回滚。
 *
 * <p>settings/mapping 仍由 classpath:es/ 下的 JSON 定义（替代 Spring Data 自动创建，
 * 保证 IK 分词器等自定义配置生效）。应用层（ProductDocument/ProductSearchRepository）
 * 永远只引用别名 "products"——别名路由由 ES 完成，业务代码零改动。</p>
 *
 * <p>兼容性：历史部署存在与别名同名的实体索引 "products"。ES 不允许别名与同名
 * 实体索引共存，首次迁移需在该索引删除与别名创建之间存在毫秒级切换窗口
 * （full-rebuild 编排内处理），之后的所有重建均零停机。</p>
 *
 * <p>仅当 {@code elasticsearch.enabled=true} 时启用。</p>
 */
@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class IndexManager {

    private static final Logger log = LoggerFactory.getLogger(IndexManager.class);
    /** 读写别名：业务层唯一可见的索引名 */
    public static final String ALIAS_NAME = "products";
    private static final String VERSIONED_PREFIX = "products_v";
    private static final String LEGACY_INDEX_NAME = "products";
    private static final String SETTINGS_PATH = "es/products-settings.json";
    private static final String MAPPING_PATH = "es/products-mapping.json";

    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;

    public IndexManager(ElasticsearchOperations operations) {
        this.operations = operations;
        this.objectMapper = new ObjectMapper();
    }

    /** @return 别名是否存在（存在即索引体系就绪） */
    public boolean indexExists() {
        return !aliasTargets().isEmpty();
    }

    /** @return 别名当前指向的实体索引（优先 write index） */
    public Optional<String> currentWriteIndex() {
        Map<String, Set<AliasData>> targets = aliasTargets();
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return targets.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(a -> Boolean.TRUE.equals(a.isWriteIndex())))
                .map(Map.Entry::getKey)
                .findFirst()
                .or(() -> targets.keySet().stream().findFirst());
    }

    /** @return 别名指向的全部实体索引（key=实体索引名，value=别名详情） */
    public Map<String, Set<AliasData>> aliasTargets() {
        try {
            Map<String, Set<AliasData>> aliases = indexOpsForAlias().getAliases(ALIAS_NAME);
            return aliases != null ? aliases : Map.of();
        } catch (org.springframework.data.elasticsearch.ResourceNotFoundException e) {
            // 别名不存在是首次启用/迁移前的正常状态（ES get-alias 对缺失别名返回 404）
            return Map.of();
        }
    }

    /**
     * @return 下一个版本化实体索引名（版本号取当前指向索引的版本 + 1；无指向索引从 v1 起）
     */
    public String nextVersionIndexName() {
        Optional<String> current = currentWriteIndex();
        int version = current.map(IndexManager::parseVersion).orElse(0);
        if (version == 0 && current.isPresent()) {
            // 当前指向的不是版本化命名（不应发生）——防御性从 v1 起算
            log.warn("[T08] 当前别名指向非版本化索引 {}，新版本从 v1 起算", current.get());
        }
        return VERSIONED_PREFIX + (version + 1);
    }

    /**
     * 创建版本化实体索引（settings + mapping），不建别名。
     *
     * @return true 表示创建成功
     */
    public boolean createVersionedIndex(String indexName) {
        IndexOperations indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            log.warn("[T08] 目标版本索引 [{}] 已存在，复用（请确认是否为残留失败重建）", indexName);
            return true;
        }
        return createIndex(indexOps, indexName);
    }

    /**
     * 原子切换别名到新实体索引：单次 _aliases 请求内移除旧指向 + 添加新指向，
     * ES 保证无中间态。切换后新索引成为唯一读写目标，旧索引保留（回滚/清理用）。
     *
     * @return true 表示切换成功
     */
    public boolean switchAliasTo(String newIndexName) {
        List<AliasAction> actions = new ArrayList<>();
        Optional<String> current = currentWriteIndex();
        current.ifPresent(old -> actions.add(new AliasAction.Remove(AliasActionParameters.builder()
                .withIndices(old)
                .withAliases(ALIAS_NAME)
                .build())));
        actions.add(new AliasAction.Add(AliasActionParameters.builder()
                .withIndices(newIndexName)
                .withAliases(ALIAS_NAME)
                .withIsWriteIndex(true)
                .build()));
        boolean switched = indexOpsForAlias().alias(new AliasActions(actions.toArray(new AliasAction[0])));
        log.info("[T08] 别名 [{}] 已切换 {}→{} (switched={})",
                ALIAS_NAME, current.orElse("(empty)"), newIndexName, switched);
        return switched;
    }

    /**
     * @return 历史遗留的同名实体索引 "products" 是否存在（别名与实体索引不能同名，
     * 存在即需一次性迁移后才能启用别名体系）
     */
    public boolean legacyIndexExists() {
        return operations.indexOps(IndexCoordinates.of(LEGACY_INDEX_NAME)).exists();
    }

    /** 删除历史遗留的同名实体索引（一次性迁移步骤，在切换窗口内调用） */
    public boolean deleteLegacyIndex() {
        boolean deleted = operations.indexOps(IndexCoordinates.of(LEGACY_INDEX_NAME)).delete();
        log.warn("[T08] 历史实体索引 [{}] 已删除（一次性迁移步骤）: {}", LEGACY_INDEX_NAME, deleted);
        return deleted;
    }

    /**
     * 首次启用：创建 v1 实体索引并建立别名（无历史索引时的初始化路径）。
     */
    public boolean bootstrapAliasSystem() {
        String first = nextVersionIndexName();
        if (!createVersionedIndex(first)) {
            return false;
        }
        return switchAliasTo(first);
    }

    /**
     * 获取当前写索引的 mapping 信息（用于诊断）。
     */
    public Map<String, Object> getIndexMapping() {
        Map<String, Object> mapping = indexOpsForAlias().getMapping();
        return mapping != null ? mapping : Map.of();
    }

    /**
     * 获取当前写索引的 settings 信息（用于诊断）。
     */
    public Map<String, Object> getIndexSettings() {
        org.springframework.data.elasticsearch.core.index.Settings settings = indexOpsForAlias().getSettings();
        return settings != null ? settings : Map.of();
    }

    private boolean createIndex(IndexOperations indexOps, String indexName) {
        try {
            Document settings = loadJsonAsDocument(SETTINGS_PATH);
            boolean created = indexOps.create(settings);
            if (!created) {
                log.error("[T08] Failed to create ES index [{}]", indexName);
                return false;
            }

            Document mapping = loadJsonAsDocument(MAPPING_PATH);
            indexOps.putMapping(mapping);

            log.info("[T08] ES index [{}] created with custom settings and mapping", indexName);
            return true;
        } catch (IOException e) {
            log.error("[T08] Failed to load index definition files for [{}]", indexName, e);
            return false;
        }
    }

    private IndexOperations indexOpsForAlias() {
        return operations.indexOps(IndexCoordinates.of(ALIAS_NAME));
    }

    private static int parseVersion(String indexName) {
        if (indexName != null && indexName.startsWith(VERSIONED_PREFIX)) {
            try {
                return Integer.parseInt(indexName.substring(VERSIONED_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                // 非版本化命名，按 0 处理
            }
        }
        return 0;
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
