package com.ai.spring_lens.controller;

import com.ai.spring_lens.service.DocumentIngestionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
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
        return Mono.fromCallable(() ->
                        Files.createTempFile("upload-", "-" + filePart.filename())
                )
                .flatMap(tempFile ->
                        filePart.transferTo(tempFile)
                                .then(Mono.fromCallable(() -> {

                                    DocumentIngestionService.IngestionResult result =
                                            ingestionService.ingest(
                                                    new FileSystemResource(tempFile),
                                                    filePart.filename()
                                            );

                                    Files.deleteIfExists(tempFile);

                                    return ResponseEntity.<Map<String, Object>>ok(
                                            Map.of(
                                                    "filename", result.fileName(),
                                                    "chunks", result.chunks(),
                                                    "status", result.status()
                                            ));
                                })))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex ->
                        Mono.just(
                                ResponseEntity.internalServerError()
                                        .body(Map.<String, Object>of(
                                                "error", "Ingestion failed",
                                                "message", ex.getMessage()
                                        ))));
    }
}