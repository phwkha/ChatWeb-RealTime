# Tài Liệu Kỹ Thuật Dự Án ChatWeb (ChatWeb Technical Documentation)

Chào mừng bạn đến với trung tâm tài liệu kỹ thuật của dự án **ChatWeb** — Nền tảng nhắn tin thời gian thực hiệu năng cao, xây dựng trên kiến trúc hướng sự kiện (Event-Driven) và lưu trữ đa dạng (Polyglot Persistence).

---

## 📑 Mục Lục Tài Liệu Cốt Lõi (Core Documentation Index)

Bộ tài liệu được tổ chức thành 3 phân hệ chính:

### 1. Kiến Trúc & Thiết Kế Hệ Thống (Architecture & Design)
- 🏛️ **[01. Kiến Trúc Hệ Thống Tổng Thể](architecture/01-system-overview.md)**: Sơ đồ Topology đa tầng, mô hình bảo mật mTLS giữa Nginx và Spring Boot, tổng quan vai trò của từng dịch vụ.
- 💡 **[02. Các Quyết Định Kiến Trúc (ADRs)](architecture/02-architecture-decisions.md)**: Phân tích nguyên nhân và sự đánh đổi:
  - *ADR-01*: Tại sao kết hợp PostgreSQL + MongoDB + Redis?
  - *ADR-02*: Tại sao dùng Kafka + Avro với 2 Consumer Group (Fast Push vs Write-Behind)?
  - *ADR-03*: Cơ chế định tuyến WebSocket đa node bằng Redis Hash & Pub/Sub.
  - *ADR-04*: Cơ chế Rate Limiting 2 tầng (Nginx + Redis Token Bucket).
  - *ADR-05*: Mô hình mã hóa đầu-cuối lai (Hybrid E2EE: RSA + AES-GCM).
  - *ADR-06*: Cơ chế Debounce 5 giây xử lý hiện diện (Online/Offline).
- 🔄 **[03. Sơ Đồ Tuần Tự Nghiệp Vụ (Sequence Diagrams)](architecture/03-sequence-diagrams.md)**: Biểu đồ Mermaid chi tiết cho:
  - Luồng gửi & nhận tin nhắn thời gian thực.
  - Luồng xác thực, cấp phát token & Single Sign-Out (`token_version`).
  - Luồng quản lý trạng thái hiện diện (Presence Lifecycle).
  - Luồng trao đổi khóa và mã hóa E2EE.

---

### 2. Thiết Kế Cơ Sở Dữ Liệu (Database Design)
- 💾 **[Thiết Kế Cơ Sở Dữ Liệu Đa Dạng](database/database-design.md)**:
  - **PostgreSQL**: Sơ đồ quan hệ ERD (Users, Roles, Permissions, Friendships, Addresses), phân tích chỉ mục và ràng buộc toàn vẹn.
  - **MongoDB**: Chi tiết Document Schema `messages`, `read_receipts`, và `system_message` với TTL Index tự hủy thông báo.
  - **Redis**: Bảng tra cứu cấu trúc dữ liệu, quy ước đặt tên Key, giá trị TTL và mục đích sử dụng.

---

### 3. Đặc Tả Giao Tiếp & Hợp Đồng Dữ Liệu (Protocols & Contracts)
- 🔌 **[Đặc Tả Giao Thức WebSocket & STOMP](api/websocket-stomp-spec.md)**: Điểm bắt tay `/ws`, xác thực JWT, cấu trúc frame gửi nhận `/app/chat/...`, `/user/queue/messages`, `/topic/public`, và chuẩn hóa lỗi `ErrorSocketResponse`.
- ⚡ **[Danh Mục Sự Kiện Kafka & Avro Schema](api/kafka-event-catalog.md)**: Danh mục các Kafka Topic, chi tiết 21 trường trong Schema `ChatMessageAvro.avsc`, chiến lược `@RetryableTopic` và hàng đợi thư chết (DLT).
- 🌐 **[Tổng Quan REST API & Quy Ước Phản Hồi](api/rest-api-overview.md)**: Cấu trúc phong bì `ApiResponse<T>`, mã lỗi i18n, danh mục các phân hệ Auth, User, Friend, Message, Upload, E2EE Key, và Admin.

---

## 🛠️ Công Nghệ Chủ Đạo (Key Tech Stack)

| Lĩnh vực | Công nghệ sử dụng |
| :--- | :--- |
| **Backend Core** | Java 21 LTS, Spring Boot 3.5.x, Spring Security 6, Spring Data (JPA, MongoDB, Redis) |
| **Realtime Messaging**| Spring WebSocket, STOMP Protocol, SockJS |
| **Event Streaming** | Apache Kafka (2 Brokers KRaft), Confluent Schema Registry, Apache Avro |
| **Databases** | PostgreSQL 16+, MongoDB 7+, Redis Stack |
| **Ingress & Security**| Nginx Alpine, mTLS (PKCS12 Keystore), Dual Rate Limiting, Hybrid E2EE |
| **Frontend** | React 18, Vite, STOMP.js, Web Crypto API, TailwindCSS |
| **Observability** | ELK Stack (Filebeat, Logstash, Elasticsearch, Kibana), Prometheus, Grafana |
| **CI / CD** | Docker, Docker Compose, Google Jib, Jenkins Pipeline |
