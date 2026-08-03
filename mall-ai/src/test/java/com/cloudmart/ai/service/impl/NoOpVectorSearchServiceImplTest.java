package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpVectorSearchServiceImplTest {

    private NoOpVectorSearchServiceImpl noOpService;

    @BeforeEach
    void setUp() {
        noOpService = new NoOpVectorSearchServiceImpl();
    }

    @Nested
    @DisplayName("semanticSearch")
    class SemanticSearchTests {

        @Test
        @DisplayName("should return empty list for any query")
        void semanticSearch_returnsEmptyList() {
            List<VectorSearchResult> results = noOpService.semanticSearch("露营装备", 10);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for null query")
        void semanticSearch_nullQuery_returnsEmptyList() {
            List<VectorSearchResult> results = noOpService.semanticSearch(null, 5);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for zero topK")
        void semanticSearch_zeroTopK_returnsEmptyList() {
            List<VectorSearchResult> results = noOpService.semanticSearch("test", 0);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("hybridSearch")
    class HybridSearchTests {

        @Test
        @DisplayName("should return empty list for any query")
        void hybridSearch_returnsEmptyList() {
            List<VectorSearchResult> results = noOpService.hybridSearch("户外装备", 20);

            assertThat(results).isEmpty();
        }
    }
}
