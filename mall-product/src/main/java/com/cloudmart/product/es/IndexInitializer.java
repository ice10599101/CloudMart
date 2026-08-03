package com.cloudmart.product.es;

import com.cloudmart.product.service.ProductSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ES 索引初始化器：应用启动时执行两级检查：
 *
 * <ol>
 *   <li>索引是否存在 — 不存在则根据 JSON 定义文件创建索引（含 settings + mapping）</li>
 *   <li>索引文档数是否为 0 — 若为 0 且 MySQL 有商品数据，自动执行全量同步
 *       （覆盖 mysqldump 等方式导入 MySQL 但未触发应用层双写的场景）</li>
 * </ol>
 *
 * <p>仅当 {@code elasticsearch.enabled=true} 时启用。
 * 通过 {@code app.es.index.auto-create=false} 可禁用启动时自动创建。
 * 通过 {@code app.es.index.auto-reindex=false} 可禁用启动时自动全量同步。</p>
 */
@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
@Order(0)
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final IndexManager indexManager;
    private final ProductSearchRepository searchRepository;
    private final ProductSyncService productSyncService;

    public IndexInitializer(IndexManager indexManager,
                            ProductSearchRepository searchRepository,
                            ProductSyncService productSyncService) {
        this.indexManager = indexManager;
        this.searchRepository = searchRepository;
        this.productSyncService = productSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String autoCreate = System.getProperty("app.es.index.auto-create", "true");
        if (!"true".equalsIgnoreCase(autoCreate)) {
            log.info("ES index auto-create disabled by app.es.index.auto-create={}", autoCreate);
            return;
        }

        log.info("Checking ES index [products] on startup...");
        boolean exists = indexManager.indexExists();
        if (!exists) {
            log.info("ES index [products] not found, creating with custom mapping...");
            boolean created = indexManager.createIndexIfAbsent();
            if (created) {
                log.info("ES index [products] initialized successfully");
            } else {
                log.warn("ES index [products] initialization failed, will fall back to auto-create on first write");
                return;
            }
        } else {
            log.info("ES index [products] exists, skip index creation");
        }

        checkAndReindexIfEmpty();
    }

    /**
     * 检查 ES 文档数量，如果为 0 则自动全量同步 MySQL 数据。
     *
     * <p>覆盖场景：通过 mysqldump / DataGrip 等方式直接导入 MySQL 数据时，
     * 应用层双写机制不会被触发，导致 ES 索引存在但文档为空。</p>
     */
    private void checkAndReindexIfEmpty() {
        String autoReindex = System.getProperty("app.es.index.auto-reindex", "true");
        if (!"true".equalsIgnoreCase(autoReindex)) {
            log.info("ES auto-reindex disabled by app.es.index.auto-reindex={}", autoReindex);
            return;
        }

        long esDocCount = searchRepository.count();
        if (esDocCount > 0) {
            log.info("ES index [products] has {} documents, skip auto-reindex", esDocCount);
            return;
        }

        log.info("ES index [products] is empty, starting auto-reindex from MySQL...");
        int synced = productSyncService.reindexAll();
        log.info("Auto-reindex completed: {} products synced to ES", synced);
    }
}
