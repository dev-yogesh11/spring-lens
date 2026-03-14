package com.ai.spring_lens.controller;

import com.ai.spring_lens.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public Mono<ResponseEntity<Map<String, Object>>> ingest(
            @RequestPart("file") FilePart filePart
    ) {
        return Mono.fromCallable(() -> {
                    Path tempFile = Files.createTempFile("upload-", "-" + filePart.filename());

                    // write file content to temp file
                    filePart.transferTo(tempFile).block();

                    int chunks = ingestionService.ingest(
                            new org.springframework.core.io.FileSystemResource(tempFile)
                    );

                    // cleanup
                    Files.deleteIfExists(tempFile);

                    return ResponseEntity.<Map<String, Object>>ok(Map.of(
                            "filename", filePart.filename(),
                            "chunks", chunks,
                            "status", "ingested"
                    ));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.internalServerError()
                                .body(Map.<String, Object>of(
                                        "error", "Ingestion failed",
                                        "message", ex.getMessage()))
                ));
    }
}