package com.ai.spring_lens.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }

    public int ingest(Resource pdfResource) {
        log.info("Starting ingestion for file={}", pdfResource.getFilename());

        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
        List<Document> documents = splitter.apply(reader.get());

        vectorStore.add(documents);

        log.info("Ingestion complete file={} chunks={}",
                pdfResource.getFilename(), documents.size());

        return documents.size();
    }
}