# Testing Strategy

## Philosophy

Tests are written alongside implementation — not deferred to a polish phase. Each phase delivers working code and its corresponding tests together.

**What we test:** business logic, error handling, HTTP contract, and integration between layers.  
**What we skip:** JPA entities, enums, DTOs, and repository interfaces — no logic to test.

## Test Types

### Unit Tests (`@ExtendWith(MockitoExtension.class)`)
- Fast, no Spring context, no database
- All dependencies mocked with Mockito
- Used for: service classes, storage logic
- Naming: `MethodName_expectedBehavior_whenCondition`

### Web Layer Tests (`@WebMvcTest`)
- Loads only the web layer (controllers + exception handlers)
- Services mocked with `@MockBean`
- Uses `MockMvc` to simulate HTTP requests and assert responses
- Verifies: status codes, response body shape, error responses

### Integration Tests (`@SpringBootTest` + Testcontainers) — Phase 5
- Spins up real PostgreSQL + Redis via Testcontainers
- Tests the full pipeline end-to-end: upload → ingest → query
- Slower, run separately from unit tests

## Tools

| Tool | Purpose |
|---|---|
| JUnit 5 | Test runner |
| Mockito | Mocking dependencies |
| MockMvc | HTTP layer simulation |
| AssertJ | Fluent assertions (`assertThat`, `assertThatThrownBy`) |
| `@TempDir` | Temporary filesystem for file storage tests |
| Testcontainers | Real DB/Redis in integration tests (Phase 5) |

## Current Coverage (Phase 1)

| Class | Test Class | Type | Tests |
|---|---|---|---|
| `FileStorageService` | `FileStorageServiceTest` | Unit | 6 |
| `DocumentService` | `DocumentServiceTest` | Unit | 10 |
| `DocumentController` + `GlobalExceptionHandler` | `DocumentControllerTest` | Web layer | 8 |
| **Total** | | | **25** |

## Planned Coverage (Phases 2–4)

| Class | Test Class | Type |
|---|---|---|
| `IngestionService` | `IngestionServiceTest` | Unit |
| `RagService` | `RagServiceTest` | Unit |
| `ConversationService` | `ConversationServiceTest` | Unit |
| `SearchService` | `SearchServiceTest` | Unit |
| `ChatController` | `ChatControllerTest` | Web layer |
| `SearchController` | `SearchControllerTest` | Web layer |
| Full pipeline | `DocumentIngestionIntegrationTest` | Integration |

## Conventions

- Test classes mirror source package structure under `src/test/java/`
- One test class per production class
- `buildX()` helper methods create test fixtures — no duplication across tests
- `ArgumentCaptor` used to verify what gets passed to mocks, not just that methods were called
