# Architecture

## System Diagram

```
Client (Postman / Swagger UI)
           │
           ▼
  Spring Boot REST API  :8081
           │
  ┌────────┴──────────────┐
  │                       │
DocumentController     ChatController / SearchController
  │                       │
DocumentService        RagService + ConversationService / SearchService
  │                       │
  │               ┌───────┴────────┐
  │               │                │
  │          VectorStore      Anthropic API
  │          (pgvector)       Claude Haiku 4.5
  │               │
  │          OpenAI API
  │          text-embedding-3-small
  │
  ├── PostgreSQL + pgvector   (document metadata + vector chunks)
  ├── Redis                   (conversation sessions + history)
  └── Local Filesystem        (raw uploaded files — Docker volume)
```

## Component Responsibilities

### DocumentService
- Accepts uploaded file (PDF / DOCX), validates content type
- Persists file to local filesystem via `FileStorageService`
- Saves document metadata to PostgreSQL with status `PENDING`
- Triggers `IngestionService.ingest()` after the transaction commits

### IngestionService
- Parses PDF / DOCX text using `TikaDocumentReader`
- Splits text into overlapping token windows via `TokenTextSplitter`
- Tags each chunk with the `documentId` for filtered retrieval
- Sends chunks to OpenAI for embedding, stores results in `PgVectorStore`
- Updates document status: `PENDING → PROCESSING → READY` (or `FAILED`)

### RagService
- Receives user message + optional `sessionId` and `documentId` filter
- Retrieves relevant chunks from `PgVectorStore` via similarity search
- Builds a RAG prompt: system instructions + retrieved context + conversation history + user message
- Calls Anthropic Claude Haiku via Spring AI `ChatClient`
- Returns the answer with source references (document name + excerpt)

### ConversationService
- Loads and persists per-session conversation history in Redis (TTL: 24h)
- Serialises turn lists as JSON; deserialises on read

### SearchService
- Performs a pure similarity search on `PgVectorStore`
- Supports filtering by document ID
- Returns chunks with source document info and relevance score

## Data Flow

### Ingestion (POST /api/documents)
```
Upload → Save file → Parse text → Chunk → Embed (OpenAI) → Store in pgvector
                                                              └── Update Document status = READY
```

### RAG Q&A (POST /api/chat)
```
User message
  → Load session history (Redis)
  → Similarity search (pgvector) → top-k chunks
  → Build prompt [system + context + history + message]
  → Call Claude Haiku (Anthropic)
  → Save turn to Redis
  → Return answer
```

### Semantic Search (POST /api/search)
```
Query text → Embed (OpenAI) → Similarity search (pgvector) → Return ranked chunks
```

## Key Design Decisions

**Spring AI abstractions throughout**
`ChatClient`, `EmbeddingModel`, and `VectorStore` are injected as interfaces. Swapping from Anthropic to OpenAI or from pgvector to Pinecone is a config change, not a code change.

**pgvector over a managed vector DB**
Runs in Docker alongside Postgres — zero extra cloud cost. Spring AI has first-class `PgVectorStore` support. Easy to replace later if scale demands it.

**Separate embedding provider**
Anthropic does not offer an embedding API. OpenAI `text-embedding-3-small` is the cheapest available option at $0.02/1M tokens.

**Redis for conversation state**
Keeps the REST API stateless. Conversation turns stored as a JSON list with a configurable TTL (default: 24h).

**Local filesystem for files**
Mounted as a Docker volume. The `StorageService` interface makes it straightforward to swap in S3 or Azure Blob if needed.

## Infrastructure (Docker Compose)

| Service | Image | Port | Purpose |
|---|---|---|---|
| postgres | postgres:16 + pgvector | 5432 | Document metadata + vector store |
| redis | redis:7-alpine | 6379 | Conversation session storage |

All data persisted via named Docker volumes.
