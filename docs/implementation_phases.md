# Implementation Phases

---

## Phase 1 — Foundation ✅

**What this phase delivers:**
A running Spring Boot REST API where you can upload, list, retrieve, and delete documents. No AI yet — just the clean base the rest of the project builds on.

**Infrastructure**
- [x] PostgreSQL + pgvector + Redis running via Docker Compose (one command to start everything)
- [x] Flyway migration — creates the `documents` table automatically on startup
- [x] `application.yml` and `application-local.yml` — separate config for local dev vs production

**Document Management API**
- [x] `POST /api/documents` — upload a PDF or DOCX file
- [x] `GET /api/documents` — list all uploaded documents
- [x] `GET /api/documents/{id}` — get metadata for a single document
- [x] `DELETE /api/documents/{id}` — delete a document and its stored file
- [x] File storage service — saves uploaded files to the local filesystem

**Code Quality**
- [x] Global exception handler — all errors return a consistent `{ code, message, timestamp }` JSON shape
- [x] Swagger UI at `http://localhost:8081/swagger-ui.html` — interactive API docs
- [x] 25 tests — `FileStorageServiceTest` (6), `DocumentServiceTest` (11), `DocumentControllerTest` (8)

---

## Phase 2 — Ingestion Pipeline ✅

**What this phase delivers:**
When you upload a document, the app automatically reads the file, breaks it into chunks, converts each chunk into a numerical vector (embedding) using OpenAI, and stores everything in the pgvector database. The document status goes `PENDING → PROCESSING → READY` in the background while the upload response returns immediately.

**AI + Vector Store Setup**
- [x] OpenAI embeddings configured — converts text chunks into vectors (`text-embedding-3-small`)
- [x] pgvector store configured — stores and searches vectors in PostgreSQL
- [x] Flyway migration — enables the `vector` extension and creates the `vector_store` table

**Ingestion Pipeline**
- [x] `AsyncConfig` — dedicated thread pool (`ingestionExecutor`) so ingestion never blocks the HTTP response
- [x] `IngestionService` — orchestrates the full pipeline:
  - Parses PDF / DOCX using Apache Tika
  - Splits text into token-sized chunks
  - Tags each chunk with the `documentId` (needed for filtered search later)
  - Sends chunks to OpenAI for embedding, then stores in pgvector
- [x] `DocumentService` updated — triggers ingestion after the upload transaction commits
- [x] Document status tracking — `PENDING` → `PROCESSING` → `READY` (or `FAILED` if anything goes wrong)

**Tests**
- [x] `IngestionServiceTest` (6 tests) — verifies status transitions, metadata enrichment, error handling, and chunk deletion

---

## Phase 3 — RAG Q&A + Chat ✅

**What this phase delivers:**
Users can ask questions about their uploaded documents and get answers grounded in the actual document content. Supports multi-turn conversations — the app remembers what was said earlier in the same session.

**AI Setup**
- [x] Anthropic Claude Haiku configured via Spring AI — the LLM that generates answers

**Conversation Sessions**
- [x] `ConversationService` — stores and retrieves chat history per session in Redis (auto-expires after 24h)

**RAG Pipeline**
- [x] `RagService` — the core Q&A logic:
  1. Searches pgvector for the most relevant document chunks
  2. Builds a prompt: system instructions + retrieved chunks + chat history + user question
  3. Sends to Claude and returns the answer with source references

**Chat API**
- [x] `POST /api/chat` — send a message with auto-generated session ID
- [x] `POST /api/chat/{sessionId}` — send a message in an existing session
- [x] `GET /api/chat/{sessionId}/history` — retrieve full conversation history
- [x] `DELETE /api/chat/{sessionId}` — clear a session
- [x] System prompt stored in `resources/prompts/` — easy to tune without code changes

**Tests**
- [x] `RagServiceTest` (5 tests) — verifies retrieval, prompt building, source resolution, history inclusion
- [x] `ConversationServiceTest` (5 tests) — verifies session save/load/delete (mocked Redis)
- [x] `ChatControllerTest` (7 tests) — verifies HTTP contract for all chat endpoints

---

## Phase 4 — Semantic Search ✅

**What this phase delivers:**
A standalone search endpoint — no chat session needed. Send a query, get back the most relevant chunks from your documents ranked by similarity. Can search across all documents or filter to a single one.

**Search API**
- [x] `POST /api/search` — search with `{ query, documentId (optional), topK (1–20, default 5) }`
- [x] Response includes each matching chunk with its source document name and a relevance score

**Under the Hood**
- [x] `SearchService` — embeds the query via OpenAI, runs similarity search in pgvector, returns ranked results

**Tests**
- [x] `SearchServiceTest` (6 tests) — verifies scoring, topK passthrough, document filter, empty results
- [x] `SearchControllerTest` (5 tests) — verifies HTTP contract including validation

---

## Phase 5 — Polish ✅

**What this phase delivers:**
Production-quality observability, input validation, and a complete README so anyone can clone the repo and run it.

- [x] Input validation — `UnsupportedFileTypeException` (415) rejects anything that isn't PDF or DOCX before it hits the pipeline
- [x] Structured logging — JSON log format via Spring Boot 3.4 native structured logging; activate with the `structured` profile
- [x] Integration tests — Testcontainers spins up a real pgvector PostgreSQL + Redis; tests the full upload → ingest → search pipeline end-to-end with a fake `EmbeddingModel` (no API calls needed)
- [x] `README.md` — setup instructions, ASCII architecture diagram, full API reference with curl examples, environment variable table

---

## Pending Improvements (post-Phase 5)

Ordered easiest → most complex. To be done in this sequence.

**Step 1 — `resolveDocumentName` tests [ ]**
`DocumentService.resolveDocumentName()` is a public method used by `RagService` and `SearchService` but has no direct tests. Add to `DocumentServiceTest`:
- normal case: ID exists → returns name
- missing ID: UUID not found → returns `"Unknown"`
- malformed UUID → returns `"Unknown"`
- null input → returns `"Unknown"`

**Step 2 — Cache system prompt [ ]**
`RagService.loadSystemPrompt()` reads `prompts/rag-system.txt` from disk on every chat call. Fix: read once at startup with `@PostConstruct`, store in a field. No behaviour change, no test changes needed.

**Step 3 — Store relative file paths [ ]**
`FileStorageService.store()` returns an absolute path (`/Users/.../uploads/uuid_file.pdf`) which is persisted in the `documents` table. If `upload-dir` changes the app breaks. Fix: store just the filename (`uuid_file.pdf`), reconstruct the full path at read time. Affects `FileStorageService` and `DocumentService.delete()`. Existing rows in the DB will have absolute paths — they are dev/test data only, safe to discard by resetting the DB.

---

## Status Legend

| Symbol | Meaning |
|---|---|
| `[x]` | Complete |
| `[~]` | In progress |
| `[ ]` | Not started |
