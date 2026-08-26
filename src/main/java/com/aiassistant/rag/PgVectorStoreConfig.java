package com.aiassistant.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Slf4j
@Configuration
@Profile("prod")
public class PgVectorStoreConfig {

    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private TokenTextSplitterConfig tokenTextSplitterConfig;

    @Resource
    private KeywordEnricher keywordEnricher;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Bean
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .url(datasourceUrl)
                .username(datasourceUsername)
                .password(datasourcePassword)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    VectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(10000)
                .build();
    }

    /**
     * 在 Spring 上下文初始化完成（PgVector 建表完成）后，幂等加载知识库文档。
     * 首次启动入库，后续重启检测到已有数据则跳过，避免产生重复向量。
     */
    @Bean
    ApplicationRunner vectorStoreInitializer(VectorStore knowledgeVectorStore, JdbcTemplate jdbcTemplate) {
        return args -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
            if (count == null || count == 0) {
                log.info("PgVector 为空，开始加载并向量化知识库文档...");
                List<Document> documents = documentLoader.loadMarkdowns();
                List<Document> splitDocuments = tokenTextSplitterConfig.splitCustomized(documents);
                List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(splitDocuments);
                knowledgeVectorStore.add(enrichedDocuments);
                log.info("知识库向量化完成，共 {} 条记录。", enrichedDocuments.size());
            } else {
                log.info("PgVector 已存在 {} 条向量，跳过重复加载。", count);
            }
        };
    }
}
