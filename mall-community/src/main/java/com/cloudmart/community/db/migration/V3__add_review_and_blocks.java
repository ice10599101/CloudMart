package com.cloudmart.community.db.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
public class V3__add_review_and_blocks extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            createUserBlocksTable(stmt);
            createSensitiveWordsTable(stmt);
            insertSensitiveWords(stmt);
            addColumnIfNotExists(stmt, "posts", "review_status",
                    "tinyint unsigned NOT NULL DEFAULT '1' COMMENT '审核状态: 0-待审核, 1-已通过, 2-已拒绝' AFTER `status`");
            addColumnIfNotExists(stmt, "posts", "review_reason",
                    "varchar(200) DEFAULT NULL COMMENT '审核拒绝原因' AFTER `review_status`");
            addIndexIfNotExists(stmt, "posts", "idx_review_status", "(`review_status`)");
            addColumnIfNotExists(stmt, "post_comments", "review_status",
                    "tinyint unsigned NOT NULL DEFAULT '1' COMMENT '审核状态: 0-待审核, 1-已通过, 2-已拒绝' AFTER `status`");
        }
    }

    private void createUserBlocksTable(Statement stmt) throws Exception {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `user_blocks` (
              `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
              `user_id` bigint unsigned NOT NULL COMMENT '拉黑者ID',
              `blocked_user_id` bigint unsigned NOT NULL COMMENT '被拉黑者ID',
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_user_block` (`user_id`, `blocked_user_id`),
              KEY `idx_blocked_user_id` (`blocked_user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户拉黑表'
            """);
        log.info("Created user_blocks table (or already exists)");
    }

    private void createSensitiveWordsTable(Statement stmt) throws Exception {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `sensitive_words` (
              `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
              `word` varchar(100) NOT NULL COMMENT '敏感词',
              `category` varchar(30) NOT NULL DEFAULT 'GENERAL' COMMENT '分类: GENERAL-通用, POLITICAL-政治, PORNOGRAPHIC-色情, ADVERTISING-广告, INSULT-辱骂',
              `level` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '级别: 1-替换, 2-审核, 3-拒绝',
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_word` (`word`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词库'
            """);
        log.info("Created sensitive_words table (or already exists)");
    }

    private void insertSensitiveWords(Statement stmt) throws Exception {
        stmt.execute("""
            INSERT IGNORE INTO `sensitive_words` (`word`, `category`, `level`) VALUES
            ('赌博', 'ADVERTISING', 3),
            ('代开发票', 'ADVERTISING', 3),
            ('刷单', 'ADVERTISING', 3),
            ('色情', 'PORNOGRAPHIC', 3),
            ('暴力', 'INSULT', 2),
            ('傻逼', 'INSULT', 2),
            ('操你', 'INSULT', 3)
            """);
        log.info("Inserted sensitive words");
    }

    private void addColumnIfNotExists(Statement stmt, String table, String column, String definition) throws Exception {
        if (!columnExists(stmt, table, column)) {
            stmt.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            log.info("Added column {} to table {}", column, table);
        } else {
            log.info("Column {} already exists in table {}, skipping", column, table);
        }
    }

    private void addIndexIfNotExists(Statement stmt, String table, String indexName, String definition) throws Exception {
        if (!indexExists(stmt, table, indexName)) {
            stmt.execute("ALTER TABLE `" + table + "` ADD INDEX `" + indexName + "` " + definition);
            log.info("Added index {} to table {}", indexName, table);
        } else {
            log.info("Index {} already exists on table {}, skipping", indexName, table);
        }
    }

    private boolean columnExists(Statement stmt, String table, String column) throws Exception {
        String sql = "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '" + column + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    private boolean indexExists(Statement stmt, String table, String indexName) throws Exception {
        String sql = "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND index_name = '" + indexName + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }
}
