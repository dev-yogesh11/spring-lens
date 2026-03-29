# SpringLens

> **Java-native AI knowledge platform.** Turn any document corpus into a queryable knowledge base with cited answers, retrieval quality measurement, and autonomous agents.

**Java 21 · Spring Boot 4.0.3 · Spring AI 2.0 · WebFlux · Docker**

---

## What It Does

Point SpringLens at any document corpus. Ask questions in natural language. Get cited, grounded answers — with the source document and page number for every claim.

```
User:        "What is the KYC updation frequency for high-risk customers?"

SpringLens:  "At least once every two years from the date of account opening
              or last KYC updation."
              → Source: rbi-nbfc-kyc-guidelines.pdf, Page 41
```

Three retrieval strategies — switchable per request at runtime, no restart required. Quality measured with RAGAS metrics so you always know which strategy performs best on your corpus.

---

## Why This Exists

Every engineering team has the same problem — documents, runbooks, policies, contracts, specs that nobody can search effectively. SpringLens solves this the Java way: Spring Boot-native, enterprise-ready, no Python required for the core platform.

Most AI knowledge platforms are Python-first and built for demos. SpringLens is built for production — multi-tenancy, JWT auth, per-tenant cost tracking, budget enforcement, circuit breakers, reactive non-blocking throughout.

---

## Quick Start

> **Everything runs in Docker. No Java, no Python, no local setup required.**

### Prerequisites

You need the following before starting:

| Requirement                                                       | Notes                           |
|-------------------------------------------------------------------|---------------------------------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Version 24+ recommended         |
| [Groq API key](https://console.groq.com)                          | Free tier works — primary LLM   |
| [OpenAI API key](https://platform.openai.com)                     | For embeddings + LLM fallback   |
| [Cohere API key](https://cohere.com)                              | Free tier works — for reranking |

---

### Step 1 — Clone the repository

```bash
git clone https://github.com/yogeshkale/spring-lens.git
cd spring-lens
```

---

### Step 2 — Configure environment

```bash
cp .env.example .env
```

Open `.env` and fill in your API keys:

```env
# ── LLM Providers ──────────────────────────────────────────────
GROQ_AI_API_KEY=gsk_...          # https://console.groq.com
OPENAI_API_KEY=sk-...            # https://platform.openai.com
COHERE_API_KEY=...               # https://cohere.com

# ── Database ────────────────────────────────────────────────────
DB_USERNAME=springlens
DB_PASSWORD=springlens

# ── Redis ───────────────────────────────────────────────────────
REDIS_PASSWORD=springlens

# ── JWT Secret (any strong random string) ───────────────────────
JWT_SECRET=change-this-to-a-strong-secret
```

> ⚠️ Never commit `.env` — it is already in `.gitignore`.

---

### Step 3 — Start the full stack

```bash
docker compose up --build
```

This builds and starts all 4 services:

```
springlens-postgres  → PostgreSQL 16 + pgvector   (port 5432)
springlens-redis     → Redis 7.4                  (port 6380)
springlens-ragas     → Python RAGAS evaluator      (port 8088)
springlens-app       → Spring Boot API             (port 8087)
```

> ⏳ First build takes 5–10 minutes (Gradle downloads dependencies). Subsequent starts are fast.

Wait for this line to confirm the app is ready:

```
springlens-app | Netty started on port 8087
```

---

### Step 4 — Verify everything is running

```bash
curl http://localhost:8087/actuator/health
# → {"status":"UP"}

curl http://localhost:8088/health
# → {"status":"ok"}
```

---

### Step 5 — Get an auth token

```bash
# Admin login
curl -X POST http://localhost:8087/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@springlens.com", "password": "Admin@123"}'

# → {"token": "eyJ...", "expires_in": 7200}

# Save the token
export TOKEN=eyJ...
```

---

### Step 6 — Ingest a document

```bash
curl -X POST http://localhost:8087/api/v1/documents/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@your-document.pdf"

# → {"filename": "your-document.pdf", "chunks": 179, "status": "SUCCESS"}
```

---

### Step 7 — Ask a question

```bash
curl -X POST http://localhost:8087/api/v1/ai/chat/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Summarise the key compliance requirements",
    "retrievalStrategy": "hybrid-rerank",
    "conversationId": "session-001",
    "memoryEnabled": true
  }'
```

---

## Docker — Day to Day Commands

```bash
# Start everything
docker compose up -d

# Stop everything (data is preserved)
docker compose down

# View logs
docker compose logs -f app

# Rebuild after code changes
docker compose up --build

# Full reset (wipes all data)
docker compose down -v && docker compose up --build
```

---

## Architecture

```
                    ┌─────────────────────────────────────┐
                    │           Spring Boot 4.0           │
                    │              WebFlux                │
                    └──────────────┬──────────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼───────┐
     │  JWT Auth +     │  │  RAG Pipeline   │  │  Admin / RAGAS │
     │  Multi-Tenancy  │  │  Query / Stream │  │  Evaluation    │
     │  Budget Control │  │  Chat + Memory  │  │  Dashboard     │
     └─────────────────┘  └────────┬────────┘  └────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼───────┐
     │ Retrieval       │  │ Provider Router │  │  Audit + Cost  │
     │ Strategy Layer  │  │ Groq → OpenAI   │  │  Tracking      │
     │ vector-only     │  │ → Ollama        │  │  Per-tenant    │
     │ hybrid (RRF)    │  │ (fallback chain)│  │  audit_events  │
     │ hybrid-rerank   │  └─────────────────┘  └────────────────┘
     └────────┬────────┘
              │
   ┌──────────┼──────────┐
   │          │          │
┌──▼──┐  ┌────▼──┐  ┌────▼───┐
│PGVec│  │Full   │  │Cohere  │
│tor  │  │Text   │  │Rerank  │
│Store│  │Search │  │v4.0-pro│
└─────┘  └───────┘  └────────┘
```

---

## Retrieval Strategies

Three strategies, switchable per request — no restart required.

| Strategy        | How It Works                            | Best For                      |
|-----------------|-----------------------------------------|-------------------------------|
| `vector-only`   | Cosine similarity on embeddings         | Semantic / conceptual queries |
| `hybrid`        | Vector + PostgreSQL FTS merged via RRF  | Keyword + semantic combined   |
| `hybrid-rerank` | Hybrid + Cohere cross-encoder reranking | Highest precision queries     |

```bash
POST /api/v1/ai/chat/query
{
  "message": "What is the KYC policy for NBFCs?",
  "retrievalStrategy": "hybrid-rerank",
  "conversationId": "test-conv-001",
  "memoryEnabled": true
}
```

---

## RAGAS Evaluation — Measured Quality

RAG quality measured with RAGAS metrics across all three strategies on 20 golden Q&A pairs.

| Strategy          | Faithfulness | Answer Relevancy | Context Precision | Context Recall |
|-------------------|--------------|------------------|-------------------|----------------|
| vector-only       | 0.8915       | 0.8596           | 0.9417            | 0.8917         |
| hybrid            | 0.8989       | 0.8601           | 0.8944            | 0.8917         |
| **hybrid-rerank** | 0.8726       | **0.8686**       | **0.9708**        | 0.8917         |

**Key finding:** Cohere reranking improves context precision by 3% (0.9417 → 0.9708) — the most relevant chunks are ranked first more reliably. Context recall is identical across strategies, confirming corpus coverage is the constant; retrieval ordering is the variable.

Evaluation runs automatically persist to PostgreSQL. A regression alert fires if faithfulness drops more than 15% below the 7-day rolling average.

---

## Core Features

### RAG Pipeline
- PDF ingestion → chunking → embedding → PGVector storage
- Three retrieval strategies switchable per request at runtime
- Cohere `rerank-v4.0-pro` cross-encoder reranking
- Reciprocal Rank Fusion (RRF) for hybrid result merging
- Streaming responses via Server-Sent Events (SSE)
- Conversation memory backed by Redis (per `conversationId`)
- Stateless query endpoints — memory advisor never leaks into non-chat calls

### Multi-Provider LLM with Fallback
```
Groq → OpenAI → Ollama (local)
```
Reactive fallback chain — if Groq fails (timeout, rate limit, error), OpenAI takes over automatically. If OpenAI fails, Ollama (local) handles the request. Fully reactive — no `.block()`, no thread starvation.

### Enterprise Controls
- **JWT Authentication** — HS512, 2-hour expiry, role-based (USER / ADMIN)
- **Multi-tenancy** — every document, query, and audit row is tenant-scoped
- **Budget Enforcement** — 6 checks per request: user/tenant × daily requests / daily tokens / monthly tokens. Returns `429` for rate limits, `402` for budget exhaustion
- **Audit Logging** — every query writes to `audit_events`: tokens, cost (USD), latency, strategy, sources cited, tenant/user IDs
- **Cost Tracking** — per-query USD cost calculated and stored. ~$0.000247 per query at current rates (~₹0.02)

### RAGAS Evaluation Pipeline
- Standalone Python FastAPI service (`springlens-ragas-evaluator`) on port 8088
- Spring Boot calls it via non-blocking WebClient
- Metrics: faithfulness, answer relevancy, context precision, context recall
- Results persisted to `ragas_evaluation_run` + `ragas_evaluation_pair` tables
- Regression alert — WARN log when faithfulness drops 15%+ below rolling average
- Quality dashboard — `GET /api/v1/admin/quality` returns per-strategy averages

---

## Tech Stack

| Layer               | Technology                                                 |
|---------------------|------------------------------------------------------------|
| Language            | Java 21                                                    |
| Framework           | Spring Boot 4.0.3 + WebFlux                                |
| AI Framework        | Spring AI 2.0                                              |
| Vector Store        | PGVector (PostgreSQL 16 + pgvector extension)              |
| Conversation Memory | Redis 7.4 (RedisChatMemoryRepository)                      |
| Schema Management   | Flyway                                                     |
| LLM Providers       | Groq (primary), OpenAI (fallback), Ollama (local fallback) |
| Embeddings          | OpenAI `text-embedding-3-small`                            |
| Reranking           | Cohere `rerank-v4.0-pro`                                   |
| Evaluation          | RAGAS (Python FastAPI service)                             |
| Observability       | Spring Actuator, 28 endpoints                              |
| Build               | Gradle 9.3.1 (wrapper)                                     |
| Containerization    | Docker + Docker Compose                                    |

---

## API Reference

### Authentication

```bash
# Admin login
POST /api/v1/auth/login
{"email": "admin@springlens.com", "password": "Admin@123"}
→ {"token": "eyJ...", "expires_in": 7200}

# User login
POST /api/v1/auth/login
{"email": "user@springlens.com", "password": "User@123"}
→ {"token": "eyJ...", "expires_in": 7200}
```

### Document Ingestion

```bash
POST /api/v1/documents/ingest
Authorization: Bearer $TOKEN
Content-Type: multipart/form-data
file=@document.pdf
→ {"filename": "...", "chunks": 179, "status": "SUCCESS"}
```

### RAG Query — Stateful (Redis memory)

```bash
POST /api/v1/ai/chat/query
Authorization: Bearer $TOKEN
{
  "message": "...",
  "retrievalStrategy": "hybrid-rerank",
  "conversationId": "session-123",
  "memoryEnabled": true
}
→ {"answer": "...", "sources": [...], "promptTokens": 1566, "completionTokens": 33, "latencyMs": 2452}
```

### Streaming Query — Stateless (SSE)

```bash
GET /api/v1/ai/chat/stream?message=...&retrievalStrategy=hybrid
Authorization: Bearer $TOKEN
→ SSE stream: data: token\n\ndata: by\n\ndata: token\n\n
```

### Conversational Chat — Stateful (Redis memory)

```bash
POST /api/v1/ai/chat
Authorization: Bearer $TOKEN
{"message": "...", "conversationId": "session-123", "memoryEnabled": true}
→ {"response": "...", "model": "...", "promptTokens": 3957}
```

### RAGAS Evaluation (Admin)

```bash
POST /api/v1/admin/evaluate
Authorization: Bearer $TOKEN
{"retrievalStrategy": "hybrid-rerank", "pairs": [...]}
→ {"scores": {"faithfulness": 0.87, "answer_relevancy": 0.87, "context_precision": 0.97, "context_recall": 0.89}}
```

### Quality Dashboard (Admin)

```bash
GET /api/v1/admin/quality?days=7
Authorization: Bearer $TOKEN
→ {"strategies": [{"retrievalStrategy": "hybrid-rerank", "avgFaithfulness": 0.87, "runCount": 9, ...}]}
```

---

## Project Structure

```
spring-lens/
├── Dockerfile                            ← Spring Boot container image
├── docker-compose.yaml                   ← Full stack orchestration
├── .dockerignore                         ← Docker build context exclusions
├── .env.example                          ← Environment variable template (safe to commit)
├── .env                                  ← Your secrets (never commit — in .gitignore)
├── build.gradle                          ← Gradle build config
├── gradlew                               ← Gradle wrapper (Gradle 9.3.1)
├── src/main/java/com/ai/spring_lens/
│   ├── client/                           # LLM provider clients (Groq, OpenAI, Ollama)
│   ├── config/                           # All configuration — LLM, budget, RAGAS, retrieval
│   ├── controller/                       # REST controllers — chat, documents, admin, auth
│   ├── model/                            # DTOs — QueryResponse, ChatResponse, RagasEvaluation
│   ├── repository/                       # JDBC repositories — audit, budget, hybrid search, RAGAS
│   ├── security/                         # JWT filter, TenantContext, authentication
│   └── service/
│       ├── SpringAiChatService           # RAG pipeline orchestrator
│       ├── ProviderRouterService         # Multi-provider fallback chain
│       ├── BudgetEnforcementService      # 6-check budget enforcement
│       ├── RagasEvaluationService        # RAGAS pipeline integration
│       ├── RagasRegressionAlertService   # Historical regression detection
│       └── strategy/                     # Retrieval strategy implementations
│           ├── VectorOnlyRetrievalStrategy
│           ├── HybridRetrievalStrategy
│           └── HybridWithRerankRetrievalStrategy
├── src/main/resources/
│   ├── application.yaml                  # All config — fully externalised via env vars
│   └── db/migration/                     # Flyway migrations (V1–V6)
└── springlens-ragas-evaluator/           ← Python RAGAS evaluation microservice
    ├── Dockerfile                        ← RAGAS container image
    ├── main.py                           ← FastAPI app
    └── requirements.txt
```

---

## Roadmap

| Phase                          | Status         | Features                                                                                                                                     |
|--------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Phase 1 — Foundation           | ✅ Complete     | LLM integration, PDF ingestion, basic RAG, streaming, Spring AI                                                                              |
| Phase 2 — Quality + Enterprise | ✅ Complete     | Hybrid search, Cohere reranking, RAGAS evaluation, JWT auth, multi-tenancy, budget enforcement, multi-provider fallback, conversation memory |
| Phase 3 — Agents               | 🔄 In Progress | MCP protocol, autonomous agents, tool calling                                                                                                |
| Phase 4 — Multi-Agent          | 📋 Planned     | Orchestrator + Search + Analysis + Action agents                                                                                             |
| Phase 5 — MLOps                | 📋 Planned     | CI/CD quality gates, Kubernetes, fine-tuning experiment                                                                                      |

---

## Key Engineering Decisions

**Spring AI over LangChain4j** — Native Spring Boot auto-configuration, Advisors API for composable RAG pipelines, built-in Micrometer observability. Full write-up in [`docs/framework-choice.md`](docs/framework-choice.md).

**WebFlux throughout** — Non-blocking reactive stack end-to-end. No `.block()` anywhere in the request path. Blocking operations (JDBC, vector search) isolated to `Schedulers.boundedElastic()`.

**Native WebClient for Ollama** — Spring AI's OpenAI abstraction leaks config when used with non-OpenAI providers. Ollama uses a native WebClient with explicit `stream: false` to prevent connection hang.

**Flyway owns schema** — `ddl-auto: none` from day one after migration. Hibernate validates, never creates. Lesson learned after a data loss incident during initial Flyway adoption.

**Retrieval Strategy as a Pattern** — `Map<String, RetrievalStrategy>` auto-injected by Spring. Adding a new strategy = one new `@Component`. Zero changes to service layer. Runtime switchable without restart.

**Docker-first deployment** — Full stack (PostgreSQL + pgvector, Redis, RAGAS Python service, Spring Boot) orchestrated via Docker Compose. Single `docker compose up --build` brings everything up from source. No local Java or Python install required to run.

---

## Troubleshooting

| Problem                   | Fix                                                                       |
|---------------------------|---------------------------------------------------------------------------|
| App not starting          | Check `docker compose logs app` — usually a missing env var               |
| DB connection error       | Ensure `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/springlens` |
| Redis connection error    | Ensure `SPRING_DATA_REDIS_HOST=redis` and `PORT=6379`                     |
| RAGAS healthcheck failing | Check `docker compose logs ragas` — ensure `PORT=8088` is set             |
| Port already in use       | `lsof -i :8087` then `kill -9 <PID>`                                      |
| Full fresh reset          | `docker compose down -v && docker compose up --build`                     |

---

## Author

**Yogesh Kale** — Senior Java Backend Engineer transitioning to AI Platform Engineering.

6 years Java/Spring Boot → building Java-native AI infrastructure for enterprise teams.

---

## License

MIT License © 2026 Yogesh Kale