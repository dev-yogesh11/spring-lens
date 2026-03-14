# Framework Choice: Spring AI over LangChain4j

## Decision
Chose Spring AI 2.0 as the primary LLM integration framework for SpringLens.

## Three Concrete Reasons

### 1. Spring Ecosystem Integration
Spring AI provides native Spring Boot auto-configuration, property binding,
and dependency injection out of the box. ChatClient, VectorStore, and
EmbeddingModel beans are auto-configured and injectable like any other
Spring bean. LangChain4j requires manual wiring and lacks native Spring
Boot starter support at the same level.

### 2. Advisors API — Composable AI Pipeline
Spring AI's Advisors API allows RAG, conversation memory, prompt caching,
guardrails, and observability to be added as composable pipeline components
without changing core business logic. Each concern is a separate Advisor.
LangChain4j has no equivalent pattern — cross-cutting concerns are mixed
into chain definitions.

### 3. Enterprise Observability Built-in
Spring AI 2.0 provides native Micrometer tracing with automatic spans for
every model interaction — zero configuration needed. Token usage, latency,
and model metadata flow automatically into Prometheus/Grafana. LangChain4j
requires manual instrumentation for equivalent observability.

## Trade-offs Acknowledged
- LangChain4j has broader model provider support currently
- LangChain4j community is larger with more examples available
- Spring AI 2.0 is still milestone release (GA: May 2026)

## Conclusion
For a Java/Spring Boot enterprise platform targeting enterprise
market, Spring AI is the correct choice. Teams already on Spring Boot
adopt it with zero friction. The Advisors API pattern scales cleanly
across all planned features.
