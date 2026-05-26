# AI Document Assistant

A RAG-based document Q&A API built with Spring Boot and Spring AI. Upload PDF or DOCX files, ask questions about their content, and get answers grounded in the actual documents. Supports multi-turn conversations and standalone semantic search.

## Architecture

```
Client
  │
  ▼
Spring Boot REST API (port 8081)
  │
  ├─► DocumentService ──► FileStorage (local disk)
  │        │
  │        └─► IngestionService (async)
  │               │
  │               ├─► Apache Tika (parse PDF/DOCX)
  │               ├─► TokenTextSplitter (chunk text)
  │               └─► OpenAI text-embedding-3-small ──► pgvector (store)
  │
  ├─► RagService
  │       ├─► pgvector (similarity search)
  │       ├─► ConversationService ──► Redis (chat history, 24h TTL)
  │       └─► Anthropic Claude Haiku (generate answer)
  │
  └─► SearchService
          └─► pgvector (similarity search, optional filter by document)
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for PostgreSQL + pgvector + Redis)
- OpenAI API key (embeddings)
- Anthropic API key (chat/Q&A)

## Quick Start

**1. Clone and configure**

```bash
git clone https://github.com/Rishav30194/ai-document-assistant.git
cd ai-document-assistant
```

Create `src/main/resources/application-local.yml` (gitignored):

```yaml
spring:
  ai:
    anthropic:
      api-key: sk-ant-YOUR_KEY
    openai:
      api-key: sk-YOUR_KEY
  jpa:
    show-sql: true
logging:
  level:
    com.rishav.aidocumentassistant: DEBUG
```

**2. Start infrastructure**

```bash
docker-compose up -d
```

**3. Run the app**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Swagger UI: http://localhost:8081/swagger-ui.html

## API Reference

### Documents

**Upload a document**
```bash
curl -X POST http://localhost:8081/api/documents \
  -F "file=@report.pdf" \
  -F "name=Q4 Report"
```
Response: `201 Created` with `{ id, name, status: "PENDING", ... }`

Ingestion runs in the background. Poll `GET /api/documents/{id}` until `status` is `READY`.

**List documents**
```bash
curl http://localhost:8081/api/documents
```

**Get document status**
```bash
curl http://localhost:8081/api/documents/{id}
```

**Delete a document**
```bash
curl -X DELETE http://localhost:8081/api/documents/{id}
```

---

### Chat (RAG Q&A)

**Ask a question (new session)**
```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What are the key findings?"}'
```
Response includes `sessionId`, `answer`, and `sources` (matching document chunks with scores).

**Continue a session (multi-turn)**
```bash
curl -X POST http://localhost:8081/api/chat/{sessionId} \
  -H "Content-Type: application/json" \
  -d '{"message": "Can you elaborate on the second point?"}'
```

**Filter answers to a specific document**
```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Summarise this document", "documentId": "{id}"}'
```

**Get conversation history**
```bash
curl http://localhost:8081/api/chat/{sessionId}/history
```

**Delete a session**
```bash
curl -X DELETE http://localhost:8081/api/chat/{sessionId}
```

---

### Semantic Search

Search for relevant chunks without starting a chat session.

```bash
curl -X POST http://localhost:8081/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "revenue growth", "topK": 5}'
```

Filter to a single document:
```bash
curl -X POST http://localhost:8081/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "revenue growth", "documentId": "{id}", "topK": 3}'
```

Response: ranked list of `{ documentId, documentName, excerpt, score }`.

---

## Running Tests

**Unit + web layer tests (no external services)**
```bash
mvn test -Dtest='!DocumentPipelineIntegrationTest'
```

**Integration tests (requires Docker)**
```bash
mvn test -Dtest=DocumentPipelineIntegrationTest
```
Testcontainers spins up a real pgvector PostgreSQL and Redis automatically. No manual setup needed.

**All tests**
```bash
mvn test
```

## Structured Logging (Production)

To enable JSON log output (Logstash format), activate the `structured` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,structured
```

Or set the Spring profile in your deployment environment: `SPRING_PROFILES_ACTIVE=structured`.

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API key for embeddings | — |
| `ANTHROPIC_API_KEY` | Anthropic API key for chat | — |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `aidocdb` |
| `DB_USER` | Database user | `aidoc` |
| `DB_PASSWORD` | Database password | `aidoc` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `UPLOAD_DIR` | Directory for stored files | `./uploads` |

## Tech Stack

| Component | Technology |
|---|---|
| API | Spring Boot 3.4, Java 21 |
| AI framework | Spring AI 1.0.2 |
| LLM (chat) | Anthropic Claude Haiku 4.5 |
| Embeddings | OpenAI text-embedding-3-small |
| Vector store | PostgreSQL + pgvector (HNSW index, cosine similarity) |
| Session storage | Redis 7 |
| Document parsing | Apache Tika |
| DB migrations | Flyway |
| API docs | Springdoc OpenAPI / Swagger UI |
| Testing | JUnit 5, Mockito, Testcontainers |
