package com.ai.spring_lens.controller.admin;

import com.ai.spring_lens.config.QualityDashboardProperties;
import com.ai.spring_lens.repository.RagasEvaluationRepository;
import com.ai.spring_lens.repository.RagasEvaluationRepository.StrategySummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Quality dashboard endpoint for SpringLens admin.
 *
 * GET /api/v1/admin/quality?days=7
 * Returns average RAGAS scores grouped by retrieval strategy
 * for all evaluation runs within the last N days.
 *
 * Default and max days are configurable via:
 * springlens.quality-dashboard.default-days
 * springlens.quality-dashboard.max-days
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/quality")
public class QualityDashboardController {

    private final RagasEvaluationRepository evaluationRepository;
    private final QualityDashboardProperties properties;

    public QualityDashboardController(
            RagasEvaluationRepository evaluationRepository,
            QualityDashboardProperties properties) {
        this.evaluationRepository = evaluationRepository;
        this.properties = properties;
    }

    /**
     * Returns average RAGAS scores by retrieval strategy
     * for all runs within the last N days.
     *
     * days defaults to springlens.quality-dashboard.default-days (default 7)
     * days is capped at springlens.quality-dashboard.max-days (default 90)
     *
     * Example:
     * GET /api/v1/admin/quality
     * GET /api/v1/admin/quality?days=30
     */
    @GetMapping
    public Mono<ResponseEntity<QualityDashboardResponse>> quality(
            @RequestParam(required = false) Integer days
    ) {
        int requestedDays = (days != null) ? days : properties.getDefaultDays();
        int safeDays = Math.clamp(requestedDays, 1, properties.getMaxDays());
        log.info("Quality dashboard requested: days={} (capped from {})",
                safeDays, requestedDays);

        return Mono.fromCallable(() ->
                        evaluationRepository.findAverageScoresByStrategy(safeDays))
                .subscribeOn(Schedulers.boundedElastic())
                .map(strategies -> {
                    QualityDashboardResponse response = new QualityDashboardResponse(
                            safeDays,
                            strategies,
                            LocalDateTime.now().toString()
                    );
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(r -> log.info(
                        "Quality dashboard returned {} strategies for last {} days",
                        r.getBody().strategies().size(), safeDays))
                .onErrorResume(ex -> {
                    log.error("Quality dashboard failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity
                            .internalServerError()
                            .<QualityDashboardResponse>build());
                });
    }

    /**
     * Response DTO for quality dashboard.
     * periodDays  — the lookback window used for this query
     * strategies  — one entry per retrieval strategy found in DB
     * generatedAt — server timestamp of this response
     */
    public record QualityDashboardResponse(
            int periodDays,
            List<StrategySummary> strategies,
            String generatedAt
    ) {}
}