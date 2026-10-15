package com.lucky.search.config;

import com.lucky.search.domain.BarDocument;
import com.lucky.search.domain.PostDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时按文档映射创建帖子、贴吧索引。索引已存在则跳过，避免覆盖已有数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent(PostDocument.class);
        createIfAbsent(BarDocument.class);
    }

    private void createIfAbsent(Class<?> documentType) {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(documentType);
        String indexName = indexOperations.getIndexCoordinates().getIndexName();
        if (indexOperations.exists()) {
            log.info("索引已存在，跳过创建，name={}", indexName);
            return;
        }
        indexOperations.createWithMapping();
        log.info("索引已创建，name={}", indexName);
    }
}
