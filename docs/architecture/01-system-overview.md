# Kiến Trúc Hệ Thống Tổng Thể (System Architecture Overview)

Tài liệu này mô tả bức tranh kiến trúc mức cao (High-Level Design - HLD) của nền tảng **ChatWeb Real-Time Messaging**. Hệ thống được xây dựng theo mô hình **Event-Driven Architecture (EDA)** kết hợp **Polyglot Persistence**, đáp ứng yêu cầu độ trễ thấp (low-latency), tính nhất quán dữ liệu, khả năng mở rộng ngang (horizontal scalability) và bảo mật nhiều lớp.

---

## 1. Sơ Đồ Kiến Trúc Hệ Thống (Architecture Topology)

Dưới đây là sơ đồ tổng thể các thành phần trong hệ sinh thái ChatWeb:

```mermaid
graph TB
    subgraph ClientLayer["🖥️ Tầng Client"]
        WebClient["React 19 + Vite SPA<br/>(Modular CSS, STOMP.js)"]
    end

    subgraph IngressLayer["🛡️ Tầng Cổng Vào & Tải (Ingress & Load Balancing)"]
        Nginx["Nginx Reverse Proxy & Load Balancer<br/>- HTTP Port 80 (Docker Host: 8080)<br/>- IP Rate Limiting (auth: 10r/m, global: 30r/s)<br/>- Upstream TLS Verification (Private CA)"]
    end

    subgraph AppLayer["⚙️ Tầng Ứng Dụng (Application Layer)"]
        Backend["Spring Boot 3.5.x (Java 21 LTS)<br/>- Spring Security 6 (JWT + OAuth2 Google)<br/>- WebSocket STOMP Message Broker (/ws)<br/>- Dynamic Rate Limiting (@RateLimit Sliding Window)<br/>- Idempotency Engine (@Idempotent)"]
    end

    subgraph EventLayer["⚡ Tầng Xử Lý Sự Kiện (Event Streaming & Buffer)"]
        Kafka["Apache Kafka Cluster (2 Brokers - KRaft)<br/>- Topics: chat-messages, system-messages, email-messages...<br/>- Write-Behind & WebSocket Routing"]
        SchemaRegistry["Confluent Schema Registry<br/>- Quản lý Avro Schemas (ChatMessageAvro)"]
    end

    subgraph StorageLayer["💾 Tầng Lưu Trữ Đa Hình (Polyglot Persistence)"]
        Postgres[("PostgreSQL 16+<br/>- Users, Roles, Permissions<br/>- Friendships, Addresses")]
        Mongo[("MongoDB 7+<br/>- Chat Messages, Read Receipts<br/>- System Messages (TTL Auto-expire)")]
        Redis[("Redis Stack<br/>- Session Routing & Pub/Sub<br/>- Presence ZSet & Heartbeat<br/>- Token Blacklist & Recent Cache<br/>- Cuckoo Filters (filter:usernames, filter:emails)<br/>- Sliding Window Rate Limit & Idempotency")]
        Cloudinary[("Cloudinary Storage<br/>- Media files, Avatars, Attachments")]
    end

    subgraph ObservabilityLayer["📊 Tầng Giám Sát & Nhật Ký (Observability)"]
        Filebeat["Filebeat Log Shipper"] --> Logstash["Logstash Pipeline"] --> Elasticsearch["Elasticsearch Store"] --> Kibana["Kibana Dashboard"]
        Prometheus["Prometheus Scraper"] --> Grafana["Grafana Visualizer"]
        Backend -. "Metrics /actuator" .-> Prometheus
        Backend -. "App Logs (JSON)" .-> Filebeat
    end

    %% Network Connections
    WebClient -->|"HTTP / WS (Port 8080)"| Nginx
    Nginx -->|"HTTPS / WSS (Port 8443)<br/>Upstream TLS Verified"| Backend
    Backend -->|"Pub / Sub & State"| Redis
    Backend -->|"Relational Data"| Postgres
    Backend -->|"Document Data"| Mongo
    Backend -->|"Produce / Consume Events"| Kafka
    Backend -->|"Schema Registry"| SchemaRegistry
    Backend -->|"Direct Upload"| Cloudinary
```

---

## 2. Chi Tiết Các Phân Tầng Cốt Lõi

### 2.1. Tầng Client (Frontend Application)
- **Công nghệ**: [React 19](file:///home/phanhuukha/Dev/ChatWeb/chatweb_fe/package.json), Vite 8, React Router DOM 7, STOMP.js.
- **Phong cách giao diện**: Kiến trúc CSS Module theo từng phân hệ (`auth.css`, `chat.css`, `admin.css`), tối ưu dung lượng tải và độ tương thích trình duyệt.
- **Giao tiếp kép**:
  - **REST Client (`apiClient.js`)**: Thực hiện các yêu cầu HTTP xác thực, quản lý profile, lịch sử tin nhắn và tải media. Tự động đính kèm `X-Idempotency-Key` với các tác vụ nhạy cảm.
  - **WebSocket Client (`useChatSocket.js`)**: Duy trì kết nối hai chiều thời gian thực qua SockJS/STOMP, tự động reconnect và heartbeat định kỳ (10s).

### 2.2. Tầng Cổng Vào & Tải (Ingress & Reverse Proxy)
- Điểm tiếp nhận lưu lượng duy nhất của toàn bộ hệ thống từ bên ngoài.
- **Rate Limiting tầng mạng**: 
  - Vùng bảo vệ xác thực: Tối đa 10 requests/phút (burst 5) cho các endpoint `/api/auth/`.
  - Vùng toàn cục: Tối đa 30 requests/giây (burst 20) cho các endpoint khác.
- **Bảo mật kết nối nội bộ**: Nginx kết nối ngược tới Spring Boot qua cổng an toàn `8443` (HTTPS) và kiểm tra tính hợp lệ của chứng chỉ thông qua chứng chỉ gốc nội bộ `rootCA.crt` (`proxy_ssl_verify on`).

### 2.3. Tầng Ứng Dụng (Spring Boot Application)
- Trung tâm điều phối nghiệp vụ backend viết trên **Java 21 LTS** và **Spring Boot 3.5.x**.
- **Kiến trúc phân lớp chuẩn mực**: `Controller` $\rightarrow$ `Service` $\rightarrow$ `Repository` $\rightarrow$ `Model / DTO`.
- **Bảo vệ chống tấn công & quá tải**:
  - Tích hợp `@RateLimit` theo thuật toán Sliding Window Log (lưu tại Redis).
  - Tích hợp `@Idempotent` dựa trên header `X-Idempotency-Key` để bảo đảm tính an toàn khi client retry.
  - Kiểm tra tức thì sự tồn tại của tài khoản thông qua **Redis Cuckoo Filter** trước khi truy vấn PostgreSQL.

### 2.4. Tầng Xử Lý Sự Kiện (Event Streaming Layer)
- Cụm Kafka gồm **2 Brokers chạy chế độ KRaft** kết hợp **Confluent Schema Registry**.
- Đảm bảo luồng xử lý bất đồng bộ không gây nghẽn:
  - Tách luồng đẩy tin nhắn nhanh cho WebSocket (`ChatConsumer`) và luồng ghi MongoDB theo lô (`DatabaseWriteBehindConsumer`).
  - Phục vụ worker gửi email xác thực OTP ngầm mà không làm chậm API đăng ký.

### 2.5. Tầng Lưu Trữ Đa Dạng (Polyglot Persistence)
Hệ thống không phụ thuộc vào một cơ sở dữ liệu duy nhất mà phân công chuyên biệt theo bản chất dữ liệu:
1. **PostgreSQL**: Lưu trữ dữ liệu quan hệ có cấu trúc khắt khe (Users, Roles, Permissions, Friendships, Addresses).
2. **MongoDB**: Lưu trữ dữ liệu phi cấu trúc, tốc độ ghi cao (Messages, Read Receipts, System Messages).
3. **Redis Stack**: Duy trì trạng thái in-memory, session routing WebSocket, bộ lọc Cuckoo Filter, hàng đợi Sliding Window và Cache.

### 2.6. Tầng Giám Sát & Nhật Ký (Observability Layer)
- **ELK Stack**: Filebeat đọc file log định dạng JSON từ backend, chuyển tới Logstash để chuẩn hóa, lưu trữ tại Elasticsearch và hiển thị qua Kibana.
- **Prometheus & Grafana**: Prometheus định kỳ cào chỉ số hoạt động từ Spring Actuator (`/actuator/prometheus`) và biểu diễn qua bảng điều khiển Grafana.
