# ChatWeb Technical Documentation

Welcome to the technical documentation hub for **ChatWeb** — a high-performance, real-time messaging platform built upon an **Event-Driven Architecture (EDA)** and a **Polyglot Persistence** model.

---

## 📑 Master Documentation Index

The documentation suite is structured into three specialized domains:

### 1. Architecture & System Design
- 🏛️ **[01. System Architecture Overview](architecture/01-system-overview.md)**: High-level system topology, Nginx Ingress proxy with upstream TLS verification, application tier layout, event streaming pipelines, polyglot storage layout, and observability infrastructure.
- 💡 **[02. Architecture Decision Records (ADR)](architecture/02-architecture-decisions.md)**: In-depth rationale, alternatives, and trade-offs for 8 foundational architectural decisions:
  - *ADR-01*: Polyglot Persistence Strategy (PostgreSQL + MongoDB + Redis Stack).
  - *ADR-02*: Asynchronous Write-Behind & Fast-Push via Kafka Dual Consumer Groups.
  - *ADR-03*: Multi-Node WebSocket Session Routing via Redis Hash & Server Pub/Sub.
  - *ADR-04*: Two-Tier Rate Limiting (Nginx Edge IP Limit + Redis Sliding Window Lua).
  - *ADR-05*: Multi-Tier Idempotency & Message Deduplication Engine (`@Idempotent`, `ws:dedup`).
  - *ADR-06*: Distributed 5-Second Presence Debounce Queue via Redis Sorted Set.
  - *ADR-07*: Redis Cuckoo Filter for Sub-Millisecond Credential Preflight & Anti-Enumeration.
  - *ADR-08*: Watermark-Based Read Receipts with MongoDB `$max` Upsert & Real-Time Fanout.
- 🔄 **[03. Core Business Sequence Diagrams](architecture/03-sequence-diagrams.md)**: Detailed Mermaid sequence diagrams for:
  - Real-Time Chat Pipeline (Deduplication $\rightarrow$ Fast-Push $\rightarrow$ Bulk Write-Behind $\rightarrow$ DLT).
  - Authentication, Token Rotation, and Single Sign-Out (`token_version` revocation).
  - Distributed Presence Lifecycle & 5-Second Debounce Queue.
  - Friend Request & Real-Time Notification Pipeline.
  - Watermark-Based Read Receipt Lifecycle.

---

### 2. Database Design & Persistence Models
- 💾 **[Polyglot Database Design](database/database-design.md)**:
  - **PostgreSQL 16+**: Authoritative relational ERD (`users`, `roles`, `permissions`, `friendships`, `addresses`), column constraints, foreign keys, and index optimization.
  - **MongoDB 7+**: Document schemas for `messages`, `read_receipts`, and `system_message`, compound indexing strategies, TTL auto-expiration, and automated initialization via `init-mongo.js`.
  - **Redis Stack**: Master key catalog, data structures, TTL rules, Cuckoo Filters (`filter:usernames`, `filter:emails`), sliding-window Lua rate limiters, and presence queues.

---

### 3. Protocols, Event Catalogs & API Contracts
- 🔌 **[WebSocket & STOMP Protocol Specification](api/websocket-stomp-spec.md)**: Handshake endpoint (`/ws`), SockJS fallback, JWT authentication in CONNECT frames, destination prefix conventions (`/app`, `/topic`, `/user/queue`), payload contracts, and standardized STOMP error handling.
- ⚡ **[Kafka Event Catalog & Avro Specifications](api/kafka-event-catalog.md)**: Cluster topology (2 KRaft brokers, Confluent Schema Registry), complete topic catalog, Apache Avro schema (`ChatMessageAvro.avsc`), `@RetryableTopic` backoff, batch Write-Behind, and Dead Letter Topic (`chat-messages-save-dlt`) fault recovery.
- 🌐 **[REST API Overview & Integration Guide](api/rest-api-overview.md)**: Standard envelope format (`ApiResponse<T>`), idempotency headers (`X-Idempotency-Key`), error structures, Swagger UI integration, and exhaustive endpoint catalogs across Auth, Users, Search, Friends, Messages, Media Uploads, Roles, Systems, and Administration.

---

## 🛠️ Key Technology Stack

| Domain | Selected Technologies |
| :--- | :--- |
| **Backend Core** | Java 21 LTS, Spring Boot 3.5.x, Spring Security 6, Spring Data JPA / MongoDB / Redis |
| **Real-Time Communication** | Spring WebSocket, STOMP Protocol, SockJS client, WebRTC |
| **Event Streaming** | Apache Kafka 3.x (2 Brokers - KRaft mode), Confluent Schema Registry, Apache Avro |
| **Persistence Tier** | PostgreSQL 16+, MongoDB 7+, Redis Stack (RedisBloom, Cuckoo Filter, Lua Scripts) |
| **Ingress & Security** | Nginx Alpine, Upstream TLS (Private CA `rootCA.crt`), IP Rate Limiting, API Idempotency |
| **Frontend SPA** | React 19, Vite 8, Modular CSS, STOMP.js |
| **Observability** | ELK Stack (Filebeat, Logstash, Elasticsearch, Kibana), Prometheus, Grafana |
| **Containerization & CI/CD**| Docker, Docker Compose, Google Jib, GitHub Actions |
