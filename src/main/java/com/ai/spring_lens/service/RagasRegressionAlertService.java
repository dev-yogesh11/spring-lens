package com.ai.spring_lens.service;

import com.ai.spring_lens.config.RegressionAlertProperties;
import com.ai.spring_lens.model.ragas.RagasEvaluationResponse;
import com.ai.spring_lens.repository.RagasEvaluationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Checks for faithfulness regression after every evaluation run.
 *
 * A regression is detected when the current evaluation's faithfulness
 * score drops by threshold-percent or more below the N-day average
 * for the same retrieval strategy.
 *
 * On regression — logs WARN. No exception thrown — alert is
 * observability only and never affects the evaluation response.
 *
 * Configurable via:
 * springlens.regression-alert.enabled          (default: true)
 * springlens.regression-alert.threshold-percent (default: 15.0)
 * springlens.regression-alert.lookback-days     (default: 7)
 */
@Slf4j
@Service
public class RagasRegressionAlertService {

    private final RagasEvaluationRepository evaluationRepository;
    private final RegressionAlertProperties properties;

    public RagasRegressionAlertService(
            RagasEvaluationRepository evaluationRepository,
            RegressionAlertProperties properties) {
        this.evaluationRepository = evaluationRepository;
        this.properties = properties;
    }

    /**
     * Checks if faithfulness has regressed compared to N-day average.
     *
     * Flow:
     * 1. Skip if regression alert is disabled in config
     * 2. Query DB for avg faithfulness for this strategy over lookback window
     * 3. Skip if no historical data exists yet (first run)
     * 4. Calculate drop percentage
     * 5. Log WARN if drop exceeds configured threshold
     *
     * Never throws — runs on boundedElastic, errors logged and swallowed.
     *
     * @param response the completed evaluation response to check
     * @return empty Mono — result is purely observability via logs
     */
    public Mono<Void> checkRegression(RagasEvaluationResponse response) {
        if (!properties.isEnabled()) {
            log.debug("Regression alert disabled — skipping check");
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            String strategy = response.retrievalStrategy();
            double currentFaithfulness = response.scores().faithfulness();
            int lookbackDays = properties.getLookbackDays();

            evaluationRepository
                    .findAverageFaithfulness(strategy, lookbackDays)
                    .ifPresentOrElse(
                            avgFaithfulness -> evaluateRegression(
                                    strategy,
                                    currentFaithfulness,
                                    avgFaithfulness),
                            () -> log.debug(
                                    "Regression check skipped — no historical data " +
                                    "for strategy={} in last {} days",
                                    strategy, lookbackDays)
                    );
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(ex -> log.error(
                "Regression alert check failed: strategy='{}' reason={}",
                response.retrievalStrategy(), ex.getMessage()))
        .onErrorResume(ex -> Mono.empty())
        .then();
    }

    /**
     * Compares current faithfulness against historical average.
     * Logs WARN if drop exceeds configured threshold percent.
     * Logs INFO if scores are healthy.
     */
    private void evaluateRegression(
            String strategy,
            double current,
            double average) {

        if (average == 0.0) {
            log.debug("Regression check skipped — historical average is zero " +
                    "for strategy={}", strategy);
            return;
        }

        double dropPercent = ((average - current) / average) * 100.0;

        if (dropPercent >= properties.getThresholdPercent()) {
            log.warn(
                    "REGRESSION ALERT: faithfulness dropped {}% below {}-day average " +
                            "| strategy={} | current={} | avg={}",
                    String.format("%.1f", dropPercent),
                    properties.getLookbackDays(),
                    strategy,
                    current,
                    average
            );
        } else {
            log.info(
                    "Regression check passed: strategy={} current={} avg={} drop={}%",
                    strategy, current, average,
                    String.format("%.1f", dropPercent)
            );
        }
    }
}