package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 恋爱知识向量库配置（内存版）。
 * 启动时把 Markdown 文档加载进 SimpleVectorStore，供 RAG 检索使用。
 * @author 33185
 */
@Configuration
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    /**
     * 构建并注入向量库 Bean。
     *
     * @param dashscopeEmbeddingModel 向量模型
     * @return 向量库实例
     */
    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        // 1. 加载原始文档。
        List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();

        // 2. 可选：自定义分片（当前先注释，按需开启）。
//        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);

        // 3. 给文档自动补充关键词元数据，提升检索表现。
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);

        // 4. 入库。
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}
