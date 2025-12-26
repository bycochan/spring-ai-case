package com.tes.springai.service;

import com.tes.springai.model.DocumentChunk;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务
 */
@Slf4j
@Service
public class VectorStoreService {

    @Autowired(required = false)
    private MilvusServiceClient milvusClient;
    
    // 内存缓存（当 Milvus 未启用时使用）
    private final Map<String, DocumentChunk> inMemoryStore = new ConcurrentHashMap<>();
    private boolean useMilvus = false;
    
    private static final String COLLECTION_NAME = "document_chunks";
    private static final int VECTOR_DIM = 1536; // 文本嵌入向量维度

    @PostConstruct
    public void init() {
        if (milvusClient == null) {
            log.warn("Milvus 客户端未启用，使用内存缓存模式。如需使用完整 RAG 功能，请在 application.yml 中设置 milvus.enabled=true 并启动 Milvus 服务。");
            useMilvus = false;
            return;
        }
        
        try {
            // 检查集合是否存在
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );

            if (hasCollection.getData()) {
                log.info("Milvus 集合已存在: {}", COLLECTION_NAME);
                useMilvus = true;
            } else {
                createCollection();
                useMilvus = true;
            }
        } catch (Exception e) {
            log.warn("Milvus 初始化失败: {}，将使用内存缓存模式", e.getMessage());
            useMilvus = false;
        }
    }

    /**
     * 创建集合
     */
    private void createCollection() {
        log.info("创建 Milvus 集合: {}", COLLECTION_NAME);

        // 定义字段
        List<FieldType> fields = new ArrayList<>();
        
        // ID 字段
        fields.add(FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());
        
        // 文档ID
        fields.add(FieldType.newBuilder()
                .withName("document_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build());
        
        // 文本内容
        fields.add(FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build());
        
        // 文件名
        fields.add(FieldType.newBuilder()
                .withName("file_name")
                .withDataType(DataType.VarChar)
                .withMaxLength(256)
                .build());
        
        // 页码
        fields.add(FieldType.newBuilder()
                .withName("page_number")
                .withDataType(DataType.Int32)
                .build());
        
        // 向量字段
        fields.add(FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build());

        // 创建集合
        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldTypes(fields)
                .build();

        R<RpcStatus> response = milvusClient.createCollection(createCollectionParam);
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("集合创建成功");
            createIndex();
            loadCollection();
        } else {
            log.error("集合创建失败: {}", response.getMessage());
        }
    }

    /**
     * 创建索引
     */
    private void createIndex() {
        log.info("创建向量索引");
        
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .build();

        R<RpcStatus> response = milvusClient.createIndex(indexParam);
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("索引创建成功");
        } else {
            log.error("索引创建失败: {}", response.getMessage());
        }
    }

    /**
     * 加载集合到内存
     */
    private void loadCollection() {
        log.info("加载集合到内存");
        
        R<RpcStatus> response = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build()
        );
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("集合加载成功");
        } else {
            log.error("集合加载失败: {}", response.getMessage());
        }
    }

    /**
     * 插入文档块
     */
    public void insertChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        if (useMilvus) {
            insertChunksToMilvus(chunks);
        } else {
            insertChunksToMemory(chunks);
        }
    }
    
    /**
     * 插入文档块到内存
     */
    private void insertChunksToMemory(List<DocumentChunk> chunks) {
        log.info("将 {} 个文档块存储到内存缓存", chunks.size());
        
        for (DocumentChunk chunk : chunks) {
            String chunkId = chunk.getChunkId() != null ? chunk.getChunkId() : UUID.randomUUID().toString();
            chunk.setChunkId(chunkId);
            inMemoryStore.put(chunkId, chunk);
        }
        
        log.info("文档块存储成功，当前缓存总数: {}", inMemoryStore.size());
    }
    
    /**
     * 插入文档块到 Milvus
     */
    private void insertChunksToMilvus(List<DocumentChunk> chunks) {

        log.info("插入 {} 个文档块到 Milvus", chunks.size());

        List<String> ids = new ArrayList<>();
        List<String> documentIds = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<Integer> pageNumbers = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            ids.add(chunk.getChunkId() != null ? chunk.getChunkId() : UUID.randomUUID().toString());
            documentIds.add(chunk.getDocumentId());
            contents.add(chunk.getContent());
            fileNames.add(chunk.getFileName() != null ? chunk.getFileName() : "");
            pageNumbers.add(chunk.getPageNumber() != null ? chunk.getPageNumber() : 0);
            embeddings.add(chunk.getEmbedding());
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", ids));
        fields.add(new InsertParam.Field("document_id", documentIds));
        fields.add(new InsertParam.Field("content", contents));
        fields.add(new InsertParam.Field("file_name", fileNames));
        fields.add(new InsertParam.Field("page_number", pageNumbers));
        fields.add(new InsertParam.Field("embedding", embeddings));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<io.milvus.grpc.MutationResult> response = milvusClient.insert(insertParam);
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("文档块插入成功");
        } else {
            log.error("文档块插入失败: {}", response.getMessage());
        }
    }

    /**
     * 向量搜索
     */
    public List<DocumentChunk> search(List<Float> queryVector, int topK) {
        if (useMilvus) {
            return searchInMilvus(queryVector, topK);
        } else {
            return searchInMemory(queryVector, topK);
        }
    }
    
    /**
     * 在内存中搜索（使用余弦相似度）
     */
    private List<DocumentChunk> searchInMemory(List<Float> queryVector, int topK) {
        log.info("在内存缓存中执行向量搜索，topK: {}，缓存总数: {}", topK, inMemoryStore.size());
        
        if (inMemoryStore.isEmpty()) {
            log.warn("内存缓存为空，无法搜索");
            return new ArrayList<>();
        }
        
        // 计算每个文档块与查询向量的余弦相似度
        List<ChunkScore> scores = new ArrayList<>();
        
        for (DocumentChunk chunk : inMemoryStore.values()) {
            if (chunk.getEmbedding() == null) {
                continue;
            }
            
            double similarity = cosineSimilarity(queryVector, chunk.getEmbedding());
            scores.add(new ChunkScore(chunk, similarity));
        }
        
        // 按相似度降序排列
        scores.sort((a, b) -> Double.compare(b.score, a.score));
        
        // 返回 top-K 结果
        List<DocumentChunk> results = scores.stream()
                .limit(topK)
                .map(cs -> cs.chunk)
                .collect(Collectors.toList());
        
        log.info("内存搜索完成，返回 {} 条结果", results.size());
        return results;
    }
    
    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1.size() != vec2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 内部类：文档块和分数
     */
    private static class ChunkScore {
        DocumentChunk chunk;
        double score;
        
        ChunkScore(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
    
    /**
     * 在 Milvus 中搜索
     */
    private List<DocumentChunk> searchInMilvus(List<Float> queryVector, int topK) {
        
        log.info("执行向量搜索，topK: {}", topK);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(topK)
                .withVectors(Arrays.asList(queryVector))
                .withVectorFieldName("embedding")
                .withOutFields(Arrays.asList("id", "document_id", "content", "file_name", "page_number"))
                .withParams("{\"nprobe\":10}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        
        List<DocumentChunk> results = new ArrayList<>();
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            
            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResultsWrapper.IDScore score = wrapper.getIDScore(0).get(i);
                
                DocumentChunk chunk = DocumentChunk.builder()
                        .chunkId((String) score.get("id"))
                        .documentId((String) score.get("document_id"))
                        .content((String) score.get("content"))
                        .fileName((String) score.get("file_name"))
                        .pageNumber(((Long) score.get("page_number")).intValue())
                        .build();
                
                results.add(chunk);
            }
            
            log.info("搜索完成，返回 {} 条结果", results.size());
        } else {
            log.error("搜索失败: {}", response.getMessage());
        }
        
        return results;
    }

    /**
     * 在指定文档中搜索
     */
    public List<DocumentChunk> searchInDocuments(List<Float> queryVector, List<String> documentIds, int topK) {
        if (useMilvus) {
            return searchInDocumentsInMilvus(queryVector, documentIds, topK);
        } else {
            return searchInDocumentsInMemory(queryVector, documentIds, topK);
        }
    }
    
    /**
     * 在指定文档中搜索（内存模式）
     */
    private List<DocumentChunk> searchInDocumentsInMemory(List<Float> queryVector, List<String> documentIds, int topK) {
        log.info("在 {} 个文档中搜索（内存模式），topK: {}", documentIds.size(), topK);
        
        Set<String> docIdSet = new HashSet<>(documentIds);
        List<ChunkScore> scores = new ArrayList<>();
        
        for (DocumentChunk chunk : inMemoryStore.values()) {
            // 过滤：只保留指定文档的块
            if (!docIdSet.contains(chunk.getDocumentId())) {
                continue;
            }
            
            if (chunk.getEmbedding() == null) {
                continue;
            }
            
            double similarity = cosineSimilarity(queryVector, chunk.getEmbedding());
            scores.add(new ChunkScore(chunk, similarity));
        }
        
        // 按相似度降序排列
        scores.sort((a, b) -> Double.compare(b.score, a.score));
        
        // 返回 top-K 结果
        List<DocumentChunk> results = scores.stream()
                .limit(topK)
                .map(cs -> cs.chunk)
                .collect(Collectors.toList());
        
        log.info("内存搜索完成，返回 {} 条结果", results.size());
        return results;
    }
    
    /**
     * 在指定文档中搜索（Milvus 模式）
     */
    private List<DocumentChunk> searchInDocumentsInMilvus(List<Float> queryVector, List<String> documentIds, int topK) {
        
        log.info("在 {} 个文档中搜索，topK: {}", documentIds.size(), topK);

        // 构建过滤表达式
        StringBuilder expr = new StringBuilder("document_id in [");
        for (int i = 0; i < documentIds.size(); i++) {
            if (i > 0) expr.append(", ");
            expr.append("\"").append(documentIds.get(i)).append("\"");
        }
        expr.append("]");

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(topK)
                .withVectors(Arrays.asList(queryVector))
                .withVectorFieldName("embedding")
                .withOutFields(Arrays.asList("id", "document_id", "content", "file_name", "page_number"))
                .withExpr(expr.toString())
                .withParams("{\"nprobe\":10}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        
        List<DocumentChunk> results = new ArrayList<>();
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            
            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResultsWrapper.IDScore score = wrapper.getIDScore(0).get(i);
                
                DocumentChunk chunk = DocumentChunk.builder()
                        .chunkId((String) score.get("id"))
                        .documentId((String) score.get("document_id"))
                        .content((String) score.get("content"))
                        .fileName((String) score.get("file_name"))
                        .pageNumber(((Long) score.get("page_number")).intValue())
                        .build();
                
                results.add(chunk);
            }
            
            log.info("搜索完成，返回 {} 条结果", results.size());
        } else {
            log.error("搜索失败: {}", response.getMessage());
        }
        
        return results;
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) {
        if (useMilvus) {
            deleteDocumentFromMilvus(documentId);
        } else {
            deleteDocumentFromMemory(documentId);
        }
    }
    
    /**
     * 从内存中删除文档
     */
    private void deleteDocumentFromMemory(String documentId) {
        log.info("从内存缓存中删除文档: {}", documentId);
        
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, DocumentChunk> entry : inMemoryStore.entrySet()) {
            if (documentId.equals(entry.getValue().getDocumentId())) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String chunkId : toRemove) {
            inMemoryStore.remove(chunkId);
        }
        
        log.info("文档删除成功，删除了 {} 个文档块", toRemove.size());
    }
    
    /**
     * 从 Milvus 中删除文档
     */
    private void deleteDocumentFromMilvus(String documentId) {
        
        log.info("删除文档: {}", documentId);
        
        String expr = String.format("document_id == \"%s\"", documentId);
        
        R<io.milvus.grpc.MutationResult> response = milvusClient.delete(
                io.milvus.param.dml.DeleteParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .withExpr(expr)
                        .build()
        );
        
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("文档删除成功");
        } else {
            log.error("文档删除失败: {}", response.getMessage());
        }
    }
}
