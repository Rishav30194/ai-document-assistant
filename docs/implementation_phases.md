# Implementation Phases

## Phase 1 — Foundation ✅
_Goal: Running Spring Boot app with document CRUD and Swagger UI._

- [x] Maven project: Java 21, Spring Boot 3.4.1, all dependencies in `pom.xml`
- [x] `docker-compose.yml`: PostgreSQL + pgvector, Redis
- [x] `Document` JPA entity + repository
- [x] `DocumentController`: upload, list, get, delete endpoints
- [x] `FileStorageService`: save/delete files on local filesystem
- [x] Flyway migration: `documents` table
- [x] Swagger / OpenAPI 3 configured and accessible at `/swagger-ui.html`
- [x] `application.yml` + `application-local.yml` structure
- [x] Global exception handler (`@ControllerAdvice`)
- [x] `FileStorageServiceTest`: 6 unit tests
- [x] `DocumentServiceTest`: 10 unit tests (Mockito)
- [x] `DocumentControllerTest`: 8 tests (`@WebMvcTest` + MockMvc)

## Phase 2 — Ingestion Pipeline
_Goal: Uploaded documents are parsed, chunked, embedded, and stored in pgvector._

- [ ] `TikaDocumentReader` integration for PDF + DOCX parsing
- [ ] `TokenTextSplitter` for chunking (configurable chunk size + overlap)
- [ ] OpenAI `EmbeddingModel` bean configured via Spring AI
- [ ] `PgVectorStore` bean configured
- [ ] Flyway migration: pgvector extension + `vector_store` table
- [ ] `IngestionService`: orchestrates parse → chunk → embed → store
- [ ] `Document` entity: add `status` field (`PENDING`, `PROCESSING`, `READY`, `FAILED`)
- [ ] Async ingestion (`@Async`) so upload endpoint returns immediately
- [ ] Chunk metadata stores `documentId` for filtering
- [ ] `IngestionServiceTest`: unit tests (mock `EmbeddingModel` + `VectorStore`)

## Phase 3 — RAG Q&A + Chat
_Goal: Users can ask questions and get answers grounded in document content, with conversation history._

- [ ] Anthropic `ChatClient` bean configured via Spring AI
- [ ] `ConversationService`: load/save turns in Redis (JSON, TTL-based)
- [ ] `RagService`: similarity search → build context prompt → call Claude
- [ ] `ChatController`: `POST /api/chat`, `GET /api/chat/{sessionId}/history`, `DELETE /api/chat/{sessionId}`
- [ ] Chat request DTO: `{ message, sessionId (optional), documentId (optional filter) }`
- [ ] Chat response DTO: `{ answer, sessionId, sources[] }`
- [ ] System prompt template (externalised to `resources/prompts/`)
- [ ] `RagServiceTest`: unit tests (mock `VectorStore` + `ChatClient`)
- [ ] `ConversationServiceTest`: unit tests (mock Redis)
- [ ] `ChatControllerTest`: `@WebMvcTest` tests

## Phase 4 — Semantic Search
_Goal: Standalone search endpoint for cross-document similarity queries._

- [ ] `SearchController`: `POST /api/search`
- [ ] `SearchService`: embed query → pgvector similarity search → rank results
- [ ] Search request DTO: `{ query, documentId (optional), topK }`
- [ ] Search response DTO: list of `{ chunk, documentId, documentName, score }`
- [ ] Support filtering by single document or search across all
- [ ] `SearchServiceTest`: unit tests (mock `EmbeddingModel` + `VectorStore`)
- [ ] `SearchControllerTest`: `@WebMvcTest` tests

## Phase 5 — Polish
_Goal: Production-quality observability, validation, and documentation._

- [ ] Structured logging (SLF4J + Logback JSON encoder)
- [ ] Input validation (`@Valid`, custom validators for file type + size)
- [ ] Actuator endpoints (`/actuator/health`, `/actuator/info`)
- [ ] Integration tests: Testcontainers for PostgreSQL + Redis (full pipeline)
- [ ] `README.md`: project description, setup steps, architecture diagram, sample requests
- [ ] Code cleanup: consistent error codes, meaningful log messages, no TODOs

## Status Legend

| Symbol | Meaning |
|---|---|
| `[ ]` | Not started |
| `[~]` | In progress |
| `[x]` | Complete |
