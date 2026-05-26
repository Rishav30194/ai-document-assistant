# AI Document Assistant — Claude Guide

RAG-based document Q&A API built with Spring Boot + Spring AI. Portfolio project demonstrating end-to-end AI engineering.

## Quick Reference

- **Language:** Java 21 (compiled on 22), Spring Boot 3.3+, Spring AI 1.0+
- **LLM:** Anthropic Claude Haiku 4.5
- **Embeddings:** OpenAI text-embedding-3-small
- **Storage:** PostgreSQL + pgvector, Redis, Local filesystem
- **Infra:** Docker Compose (all services local)

## Essential Commands

```bash
# Start all infrastructure
docker-compose up -d

# Run the app
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Run tests
mvn test

# Swagger UI
open http://localhost:8081/swagger-ui.html
```

## Environment Variables

```
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
```

## Documentation

- [`docs/project_overview.md`](docs/project_overview.md) — Goals, features, API endpoints, external services
- [`docs/architecture.md`](docs/architecture.md) — System design, components, data flow, design decisions
- [`docs/implementation_phases.md`](docs/implementation_phases.md) — Phase breakdown and task checklist

## Package Structure

```
src/main/java/com/rishav/aidocumentassistant/
├── config/        # Spring AI, Redis, Swagger config
├── controller/    # REST controllers
├── service/
│   ├── document/  # Upload, parse, chunk, embed
│   ├── chat/      # RAG retrieval, LLM, session
│   └── search/    # Semantic search
├── repository/    # JPA + VectorStore repos
├── model/         # JPA entities
├── dto/           # Request / response DTOs
└── exception/     # Custom exceptions + global handler
```
