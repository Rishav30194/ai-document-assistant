# Project Overview

## Goal

A REST API that enables intelligent document interaction through a RAG (Retrieval-Augmented Generation) pipeline. Users upload documents, ask questions about them, and receive answers grounded in the actual document content — with full multi-turn conversation support and semantic search across all uploaded documents.

## Features

| Feature | Description |
|---|---|
| Document Upload | Upload PDF or DOCX files via REST |
| Document Management | List, retrieve metadata, delete documents |
| RAG Q&A | Ask questions; answer is grounded in document content |
| Conversation History | Multi-turn chat sessions backed by Redis |
| Semantic Search | Cross-document similarity search without a chat session |

## API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/documents` | Upload a document (multipart/form-data) |
| GET | `/api/documents` | List all documents |
| GET | `/api/documents/{id}` | Get document metadata |
| DELETE | `/api/documents/{id}` | Delete document and all its chunks |
| POST | `/api/chat` | Send a message — returns RAG-grounded answer |
| GET | `/api/chat/{sessionId}/history` | Get full conversation history |
| DELETE | `/api/chat/{sessionId}` | Clear a conversation session |
| POST | `/api/search` | Semantic search across documents |

## External Services

| Service | Purpose | Model / Tier | Estimated Dev Cost |
|---|---|---|---|
| Anthropic API | LLM chat and Q&A | Claude Haiku 4.5 | ~$0.25 / 1M input tokens |
| OpenAI API | Embeddings only | text-embedding-3-small | ~$0.02 / 1M tokens |

Realistic total cost during development: **< $1**

Setup:
- Anthropic: [console.anthropic.com](https://console.anthropic.com)
- OpenAI: [platform.openai.com](https://platform.openai.com)

## Supported Document Types

- PDF (`.pdf`) — via Spring AI `TikaDocumentReader`
- Word (`.docx`) — via Apache Tika
