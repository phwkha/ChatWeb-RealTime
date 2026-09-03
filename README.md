# ChatWeb - Nền Tảng Chat Trực Tuyến

Dự án ChatWeb bao gồm Frontend (React + Vite) và Backend (Spring Boot).

## 📂 Cấu trúc dự án
- `docs/`: Toàn bộ bộ tài liệu kỹ thuật cốt lõi (Kiến trúc HLD, ADRs, Sequence Diagrams, Thiết kế DB, Đặc tả WebSocket, Kafka Avro và REST API). 👉 **[Xem mục lục tài liệu kỹ thuật](docs/README.md)**
- `chatweb_fe/`: Frontend React tối giản được khởi tạo bằng Vite.
- `chatweb_be/`: Mã nguồn Backend (Spring Boot, PostgreSQL, MongoDB, Redis, Kafka, JWT).
- `docker-compose.yml`: Cấu hình Docker Compose để khởi chạy toàn bộ hệ thống (dịch vụ, DB, Message Broker, Logging).
- `Jenkinsfile`: Định nghĩa Pipeline CI/CD cho Jenkins (kiểm thử, build Docker image, và deploy).
- `.env.example`: Mẫu các biến môi trường cấu hình cần thiết.

## 🚀 Hướng dẫn triển khai bằng Docker Compose

1. **Chuẩn bị môi trường:**
   Tạo file `.env` từ file mẫu `.env.example` và điền đầy đủ các thông tin bí mật (mật khẩu database, JWT secret, Cloudinary config...):
   ```bash
   cp .env.example .env
   ```

2. **Khởi chạy hệ thống:**
   Chạy lệnh sau tại thư mục gốc của dự án:
   ```bash
   docker-compose up -d --build
   ```

3. **Kiểm tra trạng thái:**
   ```bash
   docker-compose ps
   ```

## ⚙️ Hệ thống CI/CD (Jenkins)
Dự án được tích hợp sẵn `Jenkinsfile` với các bước tự động hóa:
- Kéo source code mới nhất.
- Chạy unit tests cho backend.
- Build và đóng gói backend qua Jib/Docker.
- Triển khai tự động (Auto Deployment) thông qua Docker.
