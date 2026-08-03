package com.cloudmart.ai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ai.vector.enabled", havingValue = "true", matchIfMissing = false)
public class VectorSearchConfig {

    @Bean
    @ConditionalOnBean(Rest5Client.class)
    public ElasticsearchVectorStore vectorStore(
            Rest5Client rest5Client,
            EmbeddingModel embeddingModel,
            @Value("${ai.vector.index-name:cloudmart_product_vectors}") String indexName
    ) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        // ES 9.x 要求 dense_vector 字段必须指定 similarity，否则 search 时 all shards failed
        options.setSimilarity(SimilarityFunction.cosine);
        return ElasticsearchVectorStore.builder(rest5Client, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .build();
    }
}
