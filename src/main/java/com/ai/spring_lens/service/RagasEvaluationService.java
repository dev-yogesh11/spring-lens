package com.ai.spring_lens.service;

import com.ai.spring_lens.config.RagasProperties;
import com.ai.spring_lens.model.ragas.RagasEvaluationRequest;
import com.ai.spring_lens.model.ragas.RagasEvaluationResponse;
import com.ai.spring_lens.repository.RagasEvaluationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * WebClient-based service for calling the Python RAGAS evaluation service.
 *
 * Non-blocking — returns Mono<RagasEvaluationResponse>.
 * RAGAS evaluation can take 10-30 seconds depending on pair count and
 * LLM scoring latency — caller should expect slow response for large batches.
 *
 * After every successful evaluation:
 * 1. Auto-saves results to DB (ragas_evaluation_run + ragas_evaluation_pair)
 * 2. Runs regression alert check — logs WARN if faithfulness drops 15%+
 *
 * Both post-evaluation steps run on Schedulers.boundedElastic() —
 * never blocks Netty event loop thread.
 *
 * Python RAGAS service: FastAPI on port 8088
 * Endpoint: POST /evaluate
 */
@Slf4j
@Service
public class RagasEvaluationService {

    private final WebClient webClient;
    private final RagasProperties properties;
    private final RagasEvaluationRepository evaluationRepository;
    private final RagasRegressionAlertService regressionAlertService;

    public RagasEvaluationService(WebClient.Builder webClientBuilder,
                                  RagasProperties properties,
                                  RagasEvaluationRepository evaluationRepository,
                                  RagasRegressionAlertService regressionAlertService) {
        this.properties = properties;
        this.evaluationRepository = evaluationRepository;
        this.regressionAlertService = regressionAlertService;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Submits evaluation pairs to RAGAS Python service, returns scores,
     * auto-saves results to database, and checks for regression.
     *
     * Flow:
     * 1. POST to Python RAGAS service — blocks 10-30s for LLM scoring
     * 2. On success — save run + pairs to DB on boundedElastic scheduler
     * 3. Run regression alert check on same scheduler
     * 4. Return response to caller regardless of DB save or alert outcome
     *
     * @param request evaluation request containing pairs and strategy name
     * @return RAGAS scores — faithfulness, answer_relevancy,
     *         context_precision, context_recall
     */
    public Mono<RagasEvaluationResponse> evaluate(RagasEvaluationRequest request) {
        if (!properties.isEnabled()) {
            log.warn("RAGAS evaluation disabled via springlens.ragas.enabled=false");
            return Mono.error(new IllegalStateException(
                    "RAGAS evaluation service is disabled. " +
                            "Set springlens.ragas.enabled=true to enable."));
        }

        log.info("Submitting RAGAS evaluation: strategy='{}' pairs={}",
                request.retrievalStrategy(), request.pairs().size());

        return webClient.post()
                .uri(properties.getEvaluatePath())
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() ||
                                status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "RAGAS service error status=" +
                                                clientResponse.statusCode() +
                                                " body=" + body))
                )
                .bodyToMono(RagasEvaluationResponse.class)
                .doOnSuccess(response -> log.info(
                        "RAGAS evaluation complete: strategy='{}' pairs={} " +
                                "faithfulness={} answerRelevancy={} " +
                                "contextPrecision={} contextRecall={}",
                        response.retrievalStrategy(),
                        response.pairCount(),
                        response.scores().faithfulness(),
                        response.scores().answerRelevancy(),
                        response.scores().contextPrecision(),
                        response.scores().contextRecall()
                ))
                .flatMap(response -> saveToDatabase(response)
                        .then(regressionAlertService.checkRegression(response))
                        .thenReturn(response))
                .doOnError(ex -> log.error(
                        "RAGAS evaluation failed: strategy='{}' reason={}",
                        request.retrievalStrategy(), ex.getMessage()
                ));
    }

    /**
     * Saves evaluation results to database on boundedElastic scheduler.
     * Offloaded from Netty event loop — JDBC is blocking.
     *
     * DB save failure is logged as error but does NOT propagate —
     * caller always receives the RAGAS scores even if persistence fails.
     */
    private Mono<UUID> saveToDatabase(RagasEvaluationResponse response) {
        return Mono.fromCallable(() -> evaluationRepository.saveEvaluation(response))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(runId -> log.info(
                        "Evaluation saved to DB: runId={} strategy={}",
                        runId, response.retrievalStrategy()))
                .doOnError(ex -> log.error(
                        "Failed to save evaluation to DB: strategy='{}' reason={}",
                        response.retrievalStrategy(), ex.getMessage()))
                .onErrorResume(ex -> Mono.empty());
    }

    /**
     * Health check — verifies Python RAGAS service is reachable.
     * Called on admin dashboard load to show service status.
     */
    public Mono<Boolean> isHealthy() {
        return webClient.get()
                .uri(properties.getHealthPath())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }
}