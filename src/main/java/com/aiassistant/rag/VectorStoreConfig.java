package com.aiassistant.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("!prod")
public class VectorStoreConfig {

    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private TokenTextSplitterConfig tokenTextSplitterConfig;

    @Resource
    private KeywordEnricher keywordEnricher;

    @Bean
    VectorStore knowledgeVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        List<Document> documentList = documentLoader.loadMarkdowns();
        List<Document> splitDocuments = tokenTextSplitterConfig.splitCustomized(documentList);
        List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(splitDocuments);
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}
