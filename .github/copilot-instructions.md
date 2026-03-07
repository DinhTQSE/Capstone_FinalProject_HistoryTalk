# History Talk Backend – AI Guide

## Project Overview
- **Stack**: Spring Boot 3.2 / Java 21 / PostgreSQL / Hibernate 6.3 / Maven
- **Root**: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend`
- **Context path**: `/Historical-tell` — all API paths are `/Historical-tell/v1/...`
- **Swagger UI**: `http://localhost:8080/Historical-tell/api/v1/swagger-ui`
- **Layer order**: Controller → Service → Repository, with DTOs between every layer boundary

## Package Structure
```
com.historyTalk
├── config/          # SecurityConfig, SpringSecurityConfig, OpenApiConfig, SwaggerConfig
├── controller/
│   ├── authentication/   # AuthController
│   └── historicalContext/ # HistoricalContextController, HistoricalContextDocumentController
├── dto/
│   ├── authentication/   # LoginRequest/Response, RegisterRequest/Response, RefreshTokenResponse
│   ├── historicalContext/ # Create/Update/Response DTOs for context and document
│   ├── exception/        # InvalidArgumentResponse
│   ├── user/             # UserInformationResponse
│   ├── ApiResponse.java
│   ├── PaginatedResponse.java
│   └── ValidationErrorResponse.java
├── entity/
│   ├── Role.java
│   ├── ContextStatus.java (enum – unused, kept for future)
│   ├── staff/     # Staff
│   ├── user/      # User, UserType (enum: REGISTERED | STAFF)
│   ├── historicalContext/ # HistoricalContext, HistoricalContextDocument
│   ├── character/ # Character, CharacterDocument
│   ├── chat/      # ChatSession, Message
│   └── quiz/      # Quiz, Question, QuizResult, QuizAnswerDetail
├── exception/
│   ├── BaseException.java (abstract, holds errorCode + HttpStatus)
│   ├── ResourceNotFoundException.java  → 404
│   ├── DataConflictException.java      → 409
│   ├── InvalidRequestException.java    → 400
│   ├── UnauthorizedException.java      → 401
│   ├── SystemException.java            → 500
│   ├── BusinessRuleViolationException  → RuntimeException (uncategorized rule breaks)
│   └── GlobalExceptionHandler.java
├── repository/    # HistoricalContextRepository, HistoricalContextDocumentRepository,
│                  # StaffRepository, UserRepository
├── security/      # JwtAuthenticationFilter, JwtTokenProvider, UserPrincipal, AuthenticatedPrincipal
├── service/
│   ├── authentication/ # AuthService (interface), AuthServiceImpl, JwtService, JwtServiceImpl,
│   │                   # CustomUserDetailsService
│   └── historicalContext/ # HistoricalContextService, HistoricalContextDocumentService
├── mapper/        # (character/, historicalContext/, user/ sub-packages)
└── utils/
    ├── authentication/
    └── SecurityUtils.java  # getStaffId(), getRoleName() from SecurityContext
```

## Entity & ID Conventions
- **All primary keys are `UUID` type** with `@GeneratedValue + @UuidGenerator` — Hibernate auto-generates, stored as PostgreSQL native `uuid` column (16 bytes).
- **No `@PrePersist` for ID generation** — only use `@PrePersist` for setting non-null default values (e.g. `isFromAi = false`).
- FK relations use `@ManyToOne` / `@OneToMany(mappedBy=...)` with `FetchType.LAZY`.
- When a service receives an ID from a controller `@PathVariable` (String), convert with `UUID.fromString(id)` before calling repository.
- When mapping entity → DTO, call `.toString()` on UUID fields.

## Exception Hierarchy — Use These, Never Create Ad-hoc
| Class | HTTP | When to use |
|---|---|---|
| `ResourceNotFoundException` | 404 | Entity not found by ID / name |
| `DataConflictException` | 409 | Duplicate name, unique constraint violation |
| `InvalidRequestException` | 400 | Business rule validation failures |
| `UnauthorizedException` | 401 | Bad credentials, invalid/expired token |
| `SystemException` | 500 | Unexpected infrastructure errors |
| `BusinessRuleViolationException` | — (RuntimeException) | Misc rule violations not covered above |

All exceptions extend `BaseException(message, errorCode, httpStatus)` except `BusinessRuleViolationException` which extends `RuntimeException` directly.  
`GlobalExceptionHandler` catches `BaseException` subclasses and returns `ErrorResponse` with `errorCode`, `message`, `status`, `timestamp`. Do NOT return `ApiResponse` for exceptions — use `ErrorResponse`.

## Request/Response Patterns
- All **successful** HTTP responses wrap payloads: `ApiResponse.success(data, "message")` or `ApiResponse.error(message, errorCode)`.
- Paginated list endpoints return `PaginatedResponse<T>` (fields: `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious`).
- Simple list endpoints (no pagination) return `List<T>` wrapped in `ApiResponse.success(...)`.
- `@Valid` on request DTOs — validation errors flow through `GlobalExceptionHandler.handleMethodArgumentNotValid` → returns `ValidationErrorResponse` with list of `{field, message}`.

## Security & Authentication
- Auth module: `POST /v1/auth/register`, `POST /v1/auth/login`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`.
- JWT stored as Bearer token; `JwtTokenProvider` signs with HS512; `JwtAuthenticationFilter` validates on each request.
- `SecurityConfig` — **shared file, coordinate before editing**:
  - `GET /Historical-tell/v1/**` → public
  - Mutating verbs (`POST/PUT/DELETE /v1/**`) → require authenticated JWT
  - `@PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")` on write endpoints
- `UserPrincipal` wraps `User` entity for Spring Security; `uid` and `staffId` are stored as `String` (`.toString()` from UUID).
- **JWT claims**: `sub` (email), `uid`, `userType`, `staffId` (STAFF only), `roleName` (STAFF only). `staffId` in the token is always `Staff.staffId` — never `User.uid`.
- **`AuthenticatedPrincipal`**: lightweight object set as the `Authentication` principal after JWT validation. Fields: `email`, `uid`, `staffId`, `roleName`, `userType`. Populated directly from JWT claims — no DB call.
- **`SecurityUtils`** (`utils/SecurityUtils.java`): use `SecurityUtils.getStaffId()` and `SecurityUtils.getRoleName()` in controllers to get caller identity. **Never** read `X-Staff-Id` / `X-Staff-Role` headers in business logic — they can be spoofed.
- `X-Staff-Id` / `X-Staff-Role` headers exist only as a fallback in `JwtAuthenticationFilter` for Swagger testing without a token. Do not add them as `@RequestHeader` parameters on any write endpoint.
- CORS is wide-open (`allowedOrigins = "*"`) with stateless sessions.

## Repository Conventions
- `HistoricalContextRepository`: `findAllSimple(search)` (list, ordered), `findAllWithSearch(search, pageable)` (paginated), `findByStaffStaffId(UUID, Pageable)`.
- `HistoricalContextDocumentRepository`: `search(keyword)`, `findByHistoricalContextContextIdOrderByUploadDateDesc(UUID)`, `findByStaffStaffIdOrderByUploadDateDesc(UUID)`.
- Use `ILIKE` (not `LOWER()`) for case-insensitive search in JPQL — Hibernate 6.3 rejects `LOWER()` on entity path expressions.
- Normalize search input to lowercase in Java before passing to `ILIKE` queries.

## Data & Validation Rules
- `HistoricalContext.name` must be unique (case-insensitive). Use `DataConflictException` for duplicates.
- `HistoricalContextDocument.content` is capped at **10 MB** (validated via UTF-8 byte length). Follow this pattern for any large text fields.
- Ownership checks on mutating ops: get `staffId` via `SecurityUtils.getStaffId()` and `staffRole` via `SecurityUtils.getRoleName()` — compare `entity.getStaff().getStaffId()` with `UUID.fromString(staffId)`; allow bypass if `staffRole.equalsIgnoreCase("ADMIN")`.
- `User.email` and `User.userName` must be unique (`unique = true` on `@Column`). `Staff.email` must also be unique.

## Developer Workflow
- **Build**: `mvn clean install` from `history-talk-backend/`
- **Run**: `mvn spring-boot:run`
- **Config**: env vars in `secretKey.properties` (gitignored) — `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS`.
- **Schema**: `DB_SCHEMA=historical_schema` (must exist in PostgreSQL before first run); `ddl-auto=update`.
- **Shared files** (coordinate before editing): `SecurityConfig.java`, `SpringSecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtTokenProvider.java`, `GlobalExceptionHandler.java`, `application.properties`, `pom.xml`.

## Adding a New Module
Follow this order: entity → repository → service → controller → DTOs → register route in `SecurityConfig`.
- Entity: use `UUID` PK with `@GeneratedValue + @UuidGenerator`, `FetchType.LAZY` for all relations.
- Repository: extend `JpaRepository<Entity, UUID>`.
- Service: inject repository, throw typed `BaseException` subclasses, map to DTO with `.toString()` on UUIDs.
- Controller: `@RequestMapping("/v1/...")`, accept IDs as `String @PathVariable`, convert to `UUID` before service call.
- Routes: register in `SecurityConfig.authorizeHttpRequests()` following existing GET-public / mutate-authenticated pattern.
