# AGENTS.md - ChatWeb Backend Development Guidelines

This file serves as the definitive reference and context guide for AI agents (and human developers) working on the **ChatWeb Backend** repository.

---

## 1. Project Overview & Architecture

**ChatWeb Backend** is a high-performance, real-time messaging backend built with **Spring Boot 3** and **Java 21**. It is designed with a hybrid/polyglot persistence model and event-driven architecture to ensure low-latency communication, scalability, and security.

### Core Tech Stack
- **Language & Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.5.x
- **Databases & Storage**:
  - **PostgreSQL**: Relational data, user accounts, authentication credentials, permissions, friendships.
  - **MongoDB**: Chat messages (`ChatMessage`), read receipts (`ReadReceipt`), system announcements (`SystemMessage`).
  - **Redis**: Caching, OTP/registration state (`RegisterData`), rate limiting, distributed WebSocket fan-out/pub-sub.
  - **Cloudinary**: Cloud storage for media files and chat attachments.
- **Messaging & Event Streaming**:
  - **Apache Kafka** with **Apache Avro** serialization (`ChatMessageAvro.avsc`) for asynchronous event pipelines (chat message persistence, notifications, email workers, message update events).
  - **Spring WebSocket / STOMP** for real-time bi-directional messaging with clients.
- **Security & Identity**:
  - Spring Security 6, Stateless JWT (Access + Refresh Token cookies).
  - OAuth2 Login (Google).
  - Role-Based Access Control (RBAC).
  - Custom Distributed Rate Limiting (`@RateLimit`).
- **Observability & Monitoring**:
  - Spring Boot Actuator, Micrometer Prometheus.
  - Centralized logging: Logback + ELK Stack (Logstash, Filebeat).
- **API Documentation**: Springdoc OpenAPI / Swagger UI (`/swagger-ui/index.html`).
- **Build & Packaging**: Apache Maven (`./mvnw`), Google Jib for containerization.

---

## 2. Directory & Package Structure

```
chatweb_be/
├── .mvn/                         # Maven wrapper configuration
├── filebeat/                     # Filebeat log collector configuration
├── logstash/                     # Logstash pipeline configuration
├── mongo/                        # MongoDB scripts/configs
├── prometheus/                   # Prometheus scraping configuration
├── src/
│   ├── main/
│   │   ├── java/com/web/backend/
│   │   │   ├── BackendApplication.java     # Application main entrypoint
│   │   │   ├── common/                     # Enums, constants, regex patterns, token types
│   │   │   ├── config/                     # Spring configurations (Security, Kafka, Redis, WebSocket, OpenAPI, etc.)
│   │   │   ├── controller/                 # REST Controllers & request/response DTOs
│   │   │   │   ├── request/                # Inbound DTOs (@Valid, validation annotations)
│   │   │   │   ├── response/               # Standard ApiResponse<T> and domain responses
│   │   │   │   └── websocket/              # WebSocket controllers & event listeners
│   │   │   ├── exception/                  # GlobalExceptionHandler, WebSocketErrorHandler, custom exceptions
│   │   │   ├── jwt/                        # JWT provider, token validation, filter chain
│   │   │   ├── kafka/                      # Kafka producers, consumers, and payload wrappers
│   │   │   ├── listener/                   # Spring application event listeners
│   │   │   ├── mapper/                     # Model-to-DTO mappers (MapStruct / manual)
│   │   │   ├── model/                      # Data Models
│   │   │   │   ├── mongo/                  # MongoDB @Document models (ChatMessage, ReadReceipt, SystemMessage)
│   │   │   │   ├── postgres/               # JPA @Entity models (UserEntity, RoleEntity, FriendshipEntity, etc.)
│   │   │   │   └── redis/                  # Redis cached data models (RegisterData, RedisWsMessage)
│   │   │   ├── oauth2/                     # Custom OAuth2 success/failure handlers & services
│   │   │   ├── ratelimit/                  # Rate-limiting annotations, Redis token-bucket aspects
│   │   │   ├── repository/                 # Spring Data JPA & Spring Data MongoDB repositories
│   │   │   ├── scheduler/                  # Scheduled background tasks (cron jobs)
│   │   │   └── service/                    # Business logic interfaces and implementations
│   │   └── resources/
│   │       ├── application.yml             # Global Spring configuration
│   │       ├── application-dev.yml         # Development profile configuration
│   │       ├── application-prod.yml        # Production profile configuration
│   │       ├── avro/                       # Avro schema files (*.avsc)
│   │       ├── i18n/                       # Internationalization bundles (messages_vi, messages_en, etc.)
│   │       └── logback-spring.xml          # Logback configuration with JSON/Logstash appenders
│   └── test/                               # Unit and integration tests (Testcontainers)
├── pom.xml                                 # Maven dependencies and build plugins
├── grafana-dashboard.json                  # Pre-configured Grafana monitoring dashboard
└── README.md                               # Project quick-start
```

---

## 3. Essential Commands

### Build & Run
- **Compile & Validate**:
  ```bash
  ./mvnw clean compile
  ```
- **Run Application Locally (Dev Profile)**:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
  ```
- **Execute Tests**:
  ```bash
  ./mvnw test
  ```
- **Package JAR**:
  ```bash
  ./mvnw clean package -DskipTests
  ```
- **Build Container Image with Google Jib**:
  ```bash
  ./mvnw compile jib:build
  ```

---

## 4. Coding Standards & Conventions

### 4.1 Response & Error Handling Standards
- **Unified API Response**: All REST endpoints must return `ResponseEntity<ApiResponse<T>>`.
  ```java
  return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), message, data));
  ```
- **Custom Exceptions**: Never throw raw generic exceptions (`RuntimeException`, `Exception`). Throw descriptive domain exceptions in `com.web.backend.exception.custom.*` (e.g., `ResourceNotFoundException`, `ResourceConflictException`, `AccessForbiddenException`).
- **Internationalization (i18n)**: Do not hardcode user-facing error or success messages in Java code. Use `Translator.toLocale("key.name")` and define translations in `src/main/resources/i18n/messages_*.properties`.

### 4.2 Data Storage Separation
- **PostgreSQL (JPA)**: Use for transactional, relational core data (`UserEntity`, `RoleEntity`, `FriendshipEntity`). Always extend `AbstractEntity` where audit timestamps are required.
- **MongoDB**: Use strictly for high-throughput, unstructured or semi-structured data like chat messages and reactions.
- **Redis**: Use for transient cache data, rate-limiting counters, and session/registration OTP states with explicit TTLs.

### 4.3 Real-Time & Event-Driven Patterns
- **Kafka for Heavy / Asynchronous Operations**: Sending emails, syncing friend requests across services, and persisting chat history to MongoDB should be decoupled using Kafka topics.
- **WebSocket STOMP Channels**: Real-time broadcasts use STOMP destinations (e.g., `/topic/...`, `/user/queue/...`).
- **Avro Schemas**: Any changes to message payload contracts on Kafka topics require updating `src/main/resources/avro/` schemas and regenerating classes.

### 4.4 Security & Authentication
- Access tokens and Refresh tokens are handled via secure, `HttpOnly`, `SameSite=Strict` cookies or standard Bearer headers.
- Protect sensitive endpoints with `@PreAuthorize("hasRole('...')")` or permission checks.
- Sensitive endpoints (auth, registration, OTP resend, chat uploads) should be annotated with `@RateLimit` to prevent abuse.

### 4.5 Logging & Observability
- Use `@Slf4j(topic = "SERVICE-OR-CONTROLLER-NAME")` for structured, clear logging.
- Avoid logging sensitive user credentials (passwords, raw tokens, secret keys).

### 4.6 Constant Declarations & String Literals (No Magic Strings)
- **Avoid Magic Strings**: Never use hardcoded inline string literals directly inside methods, request parameters, header names, or translation lookups.
- **Naming Convention**: Always declare string constants at the class level using the pattern `private static final String <NAME>_STRING = "...";` (or `_KEY`, `_PATH` when appropriate).
  ```java
  // Example:
  private static final String AVATARS_STRING = "avatars";
  private static final String VIDEO_STRING = "video";
  private static final String IMAGE_STRING = "image";
  private static final String SUCCESS_CHAT_UPLOAD_STRING = "success.chat.upload";
  ```

---

## 5. Agent Workflow & Guidelines

When modifying or adding code in this repository, follow these rules:

1. **Check Dependency Scope**: Rely on existing libraries in `pom.xml` (Spring Boot Starter, Lombok, Jackson, JJWT, Apache Avro, Cloudinary, Bucket4j/Redis). Avoid adding redundant third-party dependencies unless strictly necessary.
2. **Follow Layered Architecture**:
   - `Controller` handles HTTP routing, `@Valid` validation, and returns `ApiResponse`.
   - `Service` encapsulates pure business logic, transactions (`@Transactional`), and exception triggering.
   - `Repository` is strictly for database queries.
3. **Preserve Environment Configuration**: Never commit raw API keys, passwords, or secrets. Always reference environment variables defined in `.env` and `application-dev.yml` (e.g. `${JWT_SECRET_ACCESS}`, `${CLOUDINARY_API_KEY}`).
4. **Maintain Test Integrity**: When modifying business logic in services or controllers, update or add corresponding unit/integration tests in `src/test/java/` using Mockito or Testcontainers.
