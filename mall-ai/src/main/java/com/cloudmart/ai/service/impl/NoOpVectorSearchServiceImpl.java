package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.VectorSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "ai.vector.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVectorSearchServiceImpl implements VectorSearchService {

    @Override
    public List<VectorSearchResult> semanticSearch(String query, int topK) {
        return Collections.emptyList();
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String query, int topK) {
        return Collections.emptyList();
    }
}
