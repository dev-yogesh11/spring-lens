package com.ai.spring_lens.service;

import com.ai.spring_lens.config.IngestionProperties;
import com.ai.spring_lens.repository.HybridSearchRepository;
import com.ai.spring_lens.security.TenantContext;
import com.ai.spring_lens.service.strategy.PdfReaderStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final IngestionProperties properties;
    private final Map<String, PdfReaderStrategy> readerStrategies;
    private final HybridSearchRepository hybridSearchRepository;

    public DocumentIngestionService(VectorStore vectorStore,
                                    IngestionProperties properties,
                                    Map<String, PdfReaderStrategy> readerStrategies, HybridSearchRepository hybridSearchRepository) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .withMinChunkSizeChars(properties.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
                .withMaxNumChunks(properties.getMaxNumChunks())
                .withKeepSeparator(properties.isKeepSeparator())
                .build();
        this.readerStrategies = readerStrategies;
        this.hybridSearchRepository = hybridSearchRepository;
    }

    /**
     * Ingests a PDF resource into the vector store, tagging every chunk with
     * the tenant_id from the provided TenantContext.
     *
     * tenant_id is written as document metadata so the PGVectorStore
     * can persist it to the vector_store.tenant_id column (NOT NULL).
     *
     * This mirrors the pattern used in SpringAiChatService where TenantContext
     * is passed explicitly from the controller — no Reactor Context dependency
     * in the service layer, keeping it fully testable.
     */
    public IngestionResult ingest(Resource pdfResource, String originalFileName,
                                  TenantContext tenantContext) {
        log.info("Starting ingestion file={} tenantId={}", originalFileName,
                tenantContext.tenantId());

        if (isDuplicate(originalFileName, tenantContext)) {
            log.warn("File already ingested, skipping file={} tenantId={}",
                    originalFileName, tenantContext.tenantId());
            return IngestionResult.duplicate(originalFileName);
        }

        List<Document> documents = splitter.apply(readPdf(pdfResource));

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        documents.forEach(doc -> {
            doc.getMetadata().put("original_file_name", originalFileName);
            doc.getMetadata().put("ingestion_timestamp", timestamp);
            // ↓ This is the critical fix — tenant_id must be in metadata so
            //   PGVectorStore maps it to the vector_store.tenant_id column.
            doc.getMetadata().put("tenant_id", tenantContext.tenantId().toString());
        });

        vectorStore.add(documents);

        log.info("Ingestion complete file={} chunks={} tenantId={}",
                originalFileName, documents.size(), tenantContext.tenantId());

        return IngestionResult.success(originalFileName, documents.size());
    }

    private List<Document> readPdf(Resource pdfResource) {
        PdfReaderStrategy strategy = readerStrategies.get(properties.getPdfReaderType());
        if (strategy != null) {
            return strategy.read(pdfResource);
        }
        return readPdfAuto(pdfResource);
    }

    private List<Document> readPdfAuto(Resource pdfResource) {
        try {
            return readerStrategies.get("page").read(pdfResource);
        } catch (Exception e) {
            log.warn("Page reader failed for {}, falling back to paragraph: {}",
                    pdfResource.getFilename(), e.getMessage());
            return readerStrategies.get("paragraph").read(pdfResource);
        }
    }

    /**
     * Determines whether the uploaded file has already been ingested for the given tenant.
     * Delegates to a direct metadata query rather than vector similarity search,
     * which is both faster and semantically correct for an exact-match existence check.
     *
     * @param originalFileName the original name of the uploaded file
     * @param tenantContext    the current tenant context providing the tenant identifier
     * @return true if the file has already been ingested, false otherwise or if the check fails
     */
    private boolean isDuplicate(String originalFileName, TenantContext tenantContext) {
        try {
            return hybridSearchRepository.existsByFileNameAndTenant(
                    originalFileName,
                    String.valueOf(tenantContext.tenantId())
            );
        } catch (Exception e) {
            log.warn("Duplicate check failed, proceeding: {}", e.getMessage());
            return false;
        }
    }
    public record IngestionResult(
            String fileName,
            int chunks,
            String status
    ) {
        public static IngestionResult success(String fileName, int chunks) {
            return new IngestionResult(fileName, chunks, "ingested");
        }

        public static IngestionResult duplicate(String fileName) {
            return new IngestionResult(fileName, 0, "already_exists");
        }
    }
}