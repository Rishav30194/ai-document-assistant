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
- [x] `IngestionServiceTest` (5 tests) — verifies status transitions, metadata enrichment, and error handling

---

## Phase 3 — RAG Q&A + Chat

**What this phase delivers:**
Users can ask questions about their uploaded documents and get answers grounded in the actual document content. Supports multi-turn conversations — the app remembers what was said earlier in the same session.

**AI Setup**
- [ ] Anthropic Claude Haiku configured via Spring AI — the LLM that generates answers

**Conversation Sessions**
- [ ] `ConversationService` — stores and retrieves chat history per session in Redis (auto-expires after 24h)

**RAG Pipeline**
- [ ] `RagService` — the core Q&A logic:
  1. Searches pgvector for the most relevant document chunks
  2. Builds a prompt: system instructions + retrieved chunks + chat history + user question
  3. Sends to Claude and returns the answer with source references

**Chat API**
- [ ] `POST /api/chat` — send a message, get an AI answer back
- [ ] `GET /api/chat/{sessionId}/history` — retrieve full conversation history
- [ ] `DELETE /api/chat/{sessionId}` — clear a session
- [ ] System prompt stored in `resources/prompts/` — easy to tune without code changes

**Tests**
- [ ] `RagServiceTest` — verifies retrieval and prompt building (mocked vector store + LLM)
- [ ] `ConversationServiceTest` — verifies session save/load (mocked Redis)
- [ ] `ChatControllerTest` — verifies HTTP contract for all chat endpoints

---

## Phase 4 — Semantic Search

**What this phase delivers:**
A standalone search endpoint — no chat session needed. Send a query, get back the most relevant chunks from your documents ranked by similarity. Can search across all documents or filter to a single one.

**Search API**
- [ ] `POST /api/search` — search with `{ query, documentId (optional), topK }`
- [ ] Response includes each matching chunk with its source document name and a relevance score

**Under the Hood**
- [ ] `SearchService` — embeds the query via OpenAI, runs similarity search in pgvector, returns ranked results

**Tests**
- [ ] `SearchServiceTest` — verifies ranking and filtering logic (mocked vector store)
- [ ] `SearchControllerTest` — verifies HTTP contract

---

## Phase 5 — Polish

**What this phase delivers:**
Production-quality observability, input validation, and a complete README so anyone can clone the repo and run it.

- [ ] Input validation — reject files that are too large or not PDF/DOCX before they hit the pipeline
- [ ] Structured logging — JSON log format, useful for log aggregation tools
- [ ] Integration tests — Testcontainers spins up a real PostgreSQL + Redis in CI, tests the full upload-to-search pipeline end-to-end
- [ ] `README.md` — setup instructions, architecture diagram, and sample API requests

---

## Status Legend

| Symbol | Meaning |
|---|---|
| `[x]` | Complete |
| `[~]` | In progress |
| `[ ]` | Not started |
