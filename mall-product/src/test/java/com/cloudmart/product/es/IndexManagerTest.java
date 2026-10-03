package com.cloudmart.product.es;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasData;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T08 版本驱动投影：IndexManager 别名解析——别名缺失（首次启用/迁移前）必须
 * 视为空集而非抛错（ES get-alias 对缺失别名返回 404 → ResourceNotFoundException），
 * 启动期 IndexInitializer 依赖该语义走 bootstrap 分支。
 */
@DisplayName("IndexManager 别名解析")
class IndexManagerTest {

    private ElasticsearchOperations operations;
    private IndexOperations indexOperations;
    private IndexManager indexManager;

    @BeforeEach
    void setUp() {
        operations = mock(ElasticsearchOperations.class);
        indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        indexManager = new IndexManager(operations);
    }

    @Test
    @DisplayName("别名缺失（ResourceNotFoundException）→ 空集，indexExists=false，版本从 v1 起算")
    void aliasMissing_treatedAsEmpty() {
        when(indexOperations.getAliases(IndexManager.ALIAS_NAME))
                .thenThrow(new ResourceNotFoundException("alias [products] missing"));

        assertThat(indexManager.indexExists()).isFalse();
        assertThat(indexManager.aliasTargets()).isEmpty();
        assertThat(indexManager.currentWriteIndex()).isEmpty();
        assertThat(indexManager.nextVersionIndexName()).isEqualTo("products_v1");
    }

    @Test
    @DisplayName("别名存在 → 解析 write index 与下一版本号")
    void aliasPresent_resolvesWriteIndexAndNextVersion() {
        AliasData write = AliasData.of(IndexManager.ALIAS_NAME, null, null, null, true, false);
        AliasData readOnly = AliasData.of(IndexManager.ALIAS_NAME, null, null, null, false, false);
        when(indexOperations.getAliases(IndexManager.ALIAS_NAME)).thenReturn(Map.of(
                "products_v2", Set.of(write),
                "products_v1", Set.of(readOnly)));

        assertThat(indexManager.indexExists()).isTrue();
        assertThat(indexManager.currentWriteIndex()).contains("products_v2");
        assertThat(indexManager.nextVersionIndexName()).isEqualTo("products_v3");
    }
}
