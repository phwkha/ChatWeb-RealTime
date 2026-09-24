# ChatWeb — High-Performance Real-Time Messaging Platform

ChatWeb is an enterprise-grade, real-time messaging web application engineered with an **Event-Driven Architecture (EDA)**, **Polyglot Persistence**, and multi-tier security controls.

---

## 🌟 Key Architectural Highlights

- **Polyglot Persistence**: Segregates relational identity and access control (PostgreSQL 16+), high-throughput message streams and read receipts (MongoDB 7+), and in-memory routing and session states (Redis Stack).
- **Decoupled Real-Time & Write-Behind**: Leverages Apache Kafka (KRaft mode) with Apache Avro schemas to split message delivery into sub-10ms WebSocket fast-push and batch database persistence with Dead Letter Topic (DLT) fault isolation.
- **Distributed Session Routing**: Coordinates multi-instance WebSocket nodes behind Nginx using Redis Hash routing tables and targeted Pub/Sub channels.
- **Sub-Millisecond Credential Preflight**: Employs Redis Cuckoo Filters (`filter:usernames`, `filter:emails`) to reject invalid authentication and registration attempts in memory before querying PostgreSQL.
- **Distributed Presence Debouncing**: Queues disconnected sessions into a Redis Sorted Set (`presence:offline_queue`) with a 5-second deadline, completely eliminating presence flapping caused by page reloads or transient network handoffs.
- **Watermark Read Receipts**: Tracks conversation read states via atomic MongoDB `$max` upserts and Redis caching, avoiding write amplification.
- **Defense-in-Depth Security**: Two-tier rate limiting (Nginx IP + Redis sliding window Lua), REST API idempotency (`@Idempotent`), STOMP deduplication (`ws:dedup`), and upstream TLS verification with a private Root Certificate Authority.

---

## 📂 Repository Structure

```text
.
├── .github/workflows/      # GitHub Actions CI/CD workflows (backend & frontend CI/CD)
├── chatweb_be/             # Spring Boot 3.5.x Backend (Java 21 LTS, JPA, MongoDB, Kafka, Redis)
│   ├── initdb.sql          # PostgreSQL schema initialization script
│   ├── init-mongo.js       # MongoDB collection and compound index setup
│   └── src/                # Backend application and integration test suites
├── chatweb_fe/             # React 19 Frontend SPA (Vite 8, Modular CSS, STOMP.js, WebRTC)
├── docs/                   # Complete Technical Documentation Hub
│   ├── README.md           # Master documentation index
│   ├── architecture/       # System topology, 8 ADRs, and 5 Mermaid sequence diagrams
│   ├── database/           # PostgreSQL ERD, MongoDB schemas, and Redis key catalog
│   └── api/                # WebSocket/STOMP specs, Kafka Avro catalog, and REST API guide
├── nginx/                  # Nginx configuration (reverse proxy, edge rate-limiting)
├── ssl/                    # Private CA and backend PKCS12 keystore (Upstream TLS)
└── docker-compose.yml      # Multi-container orchestration (App, DBs, Kafka, ELK, Prometheus)
```

👉 **[Browse Full Technical Documentation](docs/README.md)**

---

## 🛠️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21 LTS, Spring Boot 3.5.x, Spring Security 6, Spring Data JPA / MongoDB / Redis |
| **Frontend** | React 19, Vite 8, React Router DOM 7, Modular CSS, STOMP.js, WebRTC |
| **Real-Time & Events** | Spring WebSocket (STOMP), SockJS, Apache Kafka 3.x (KRaft), Confluent Schema Registry, Apache Avro |
| **Databases & Cache** | PostgreSQL 16+, MongoDB 7+, Redis Stack (RedisBloom, Lua Scripts) |
| **Proxy & Ingress** | Nginx Alpine, Upstream TLS (`rootCA.crt`), IP Rate Limiting |
| **Observability** | Elasticsearch, Logstash, Kibana (ELK), Filebeat, Prometheus, Grafana |
| **DevOps & Cloud** | Docker, Docker Compose, Google Jib, GitHub Actions, Cloudinary Media Cloud |

---

## 🚀 Local Deployment with Docker Compose

### 1. Prerequisites
- [Docker Engine](https://docs.docker.com/engine/install/) (v24.0+)
- [Docker Compose](https://docs.docker.com/compose/install/) (v2.20+)

### 2. Prepare Environment Configuration
Clone the repository and instantiate your `.env` configuration from the provided sample:

```bash
cp .env.example .env
```

Review and adjust secrets in `.env` (database credentials, JWT secrets, Cloudinary keys, etc.).

### 3. Launch the Stack
Launch all services in detached mode:

```bash
docker compose up -d --build
```

### 4. Verify System Health
Check container health statuses:

```bash
docker compose ps
```

All primary services (`cw_backend`, `cw_frontend`, `nginx_lb`, `cw_postgres`, `cw_mongo`, `cw_redis`, `kafka`, `kafka2`, `schema-registry`) will report healthy.

### 5. Access Points & Dashboards

| Service | Address | Credentials / Notes |
| :--- | :--- | :--- |
| **ChatWeb Frontend** | `http://localhost` (or `https://localhost`) | Web Client SPA |
| **REST API / Swagger UI**| `http://localhost/swagger-ui/index.html` | Interactive OpenAPI documentation |
| **WebSocket Gateway** | `ws://localhost/ws` | STOMP endpoint with SockJS fallback |
| **Prometheus** *(optional)* | `http://localhost:9090` | Run with `--profile monitoring` |
| **Grafana** *(optional)* | `http://localhost/grafana/` | Run with `--profile monitoring` |
| **Kibana** *(optional)* | `http://localhost/kibana` | Run with `--profile monitoring` |

---

## 📖 Technical Documentation

For detailed architectural specifications, database schemas, and protocol contracts, refer to the documentation suite:

- [System Architecture Overview](docs/architecture/01-system-overview.md)
- [Architecture Decision Records (ADRs)](docs/architecture/02-architecture-decisions.md)
- [Core Sequence Diagrams](docs/architecture/03-sequence-diagrams.md)
- [Polyglot Database Design](docs/database/database-design.md)
- [WebSocket & STOMP Protocol Specification](docs/api/websocket-stomp-spec.md)
- [Kafka Event Catalog & Avro Schema](docs/api/kafka-event-catalog.md)
- [REST API Overview & Integration Guide](docs/api/rest-api-overview.md)
