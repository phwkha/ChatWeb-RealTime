# Sơ Đồ Tuần Tự Nghiệp Vụ Cốt Lõi (Sequence Diagrams)

Tài liệu này cung cấp các sơ đồ tuần tự chi tiết mô tả sự tương tác giữa Client, Nginx, Spring Boot Backend, Redis, Kafka và Database cho 4 luồng nghiệp vụ quan trọng nhất của hệ thống ChatWeb.

---

## 1. Luồng Gửi và Nhận Tin Nhắn Thời Gian Thực (Real-time Chat Pipeline)

Sơ đồ thể hiện toàn bộ hành trình của một tin nhắn từ khi người gửi nhấn "Gửi" cho đến khi người nhận hiển thị tin nhắn trên màn hình và tin nhắn được lưu vĩnh viễn vào MongoDB.

```mermaid
sequenceDiagram
    autonumber
    actor Sender as 👤 Sender (Client A)
    participant Nginx as 🛡️ Nginx Proxy
    participant WSInterceptor as 🔍 ChannelInterceptor
    participant Controller as 🎮 ChatController
    participant Service as ⚙️ ChatServiceImpl
    participant Redis as ⚡ Redis (Memory)
    participant Kafka as 📨 Kafka (chat-messages)
    participant ChatConsumer as 🚀 ChatConsumer (Fast Push)
    participant WSRouting as 🧭 WebSocketRoutingService
    participant SaveConsumer as 💾 DBWriteBehindConsumer
    participant Mongo as 🍃 MongoDB
    actor Recipient as 👥 Recipient (Client B)

    Sender->>Nginx: SEND /app/chat/sendPrivateMessage (STOMP frame)
    Nginx->>WSInterceptor: Forward mTLS qua Port 8443
    WSInterceptor->>WSInterceptor: Kiểm tra JWT Token & Blacklist trong Redis
    WSInterceptor->>Controller: Chuyển tiếp message hợp lệ
    Controller->>Service: sendPrivateMessage(sender, request)
    
    rect rgb(240, 248, 255)
        note over Service, Redis: Bước 1: Khử trùng lặp & Kiểm tra giới hạn
        Service->>Redis: SETNX ws:dedup:{sender}:{localId} (TTL=300s)
        alt Đã tồn tại key (Duplicate packet)
            Redis-->>Service: Return FALSE
            Service-->>Sender: Bỏ qua (Tránh spam/lặp gói tin)
        else Key mới hợp lệ
            Redis-->>Service: Return TRUE
        end
        Service->>Service: Check Rate Limit (Tối đa 30 msgs/phút)
        Service->>Service: Validate quan hệ bạn bè & trạng thái user
    end

    rect rgb(255, 250, 240)
        note over Service, Redis: Bước 2: Caching tin nhắn gần nhất
        Service->>Redis: Lưu vào chat:recent:hash & chat:recent:zset
    end

    rect rgb(245, 255, 250)
        note over Service, Kafka: Bước 3: Đẩy vào Event Stream Kafka
        Service->>Service: Map sang ChatMessageAvro (kèm localId)
        Service->>Kafka: sendChatMessage(ChatMessageAvro)
    end

    par Nhánh A: Đẩy tức thì cho người nhận (Độ trễ < 30ms)
        Kafka->>ChatConsumer: Consume (Group: chat-websocket-group)
        ChatConsumer->>WSRouting: routeMessage(recipient, "/queue/messages", DTO)
        WSRouting->>Redis: Kiểm tra target node của recipient (ws:routing:servers)
        WSRouting->>Recipient: Push tin nhắn qua STOMP /user/queue/messages
        ChatConsumer->>WSRouting: routeMessage(sender, "/queue/messages", ACK DTO)
        WSRouting->>Sender: Trả ACK xác nhận tin đã gửi (khớp localId)
    and Nhánh B: Ghi đệm vào MongoDB (Write-Behind)
        Kafka->>SaveConsumer: Consume Batch (Group: chat-save-group)
        SaveConsumer->>SaveConsumer: Lọc tin nhắn hợp lệ & chuyển đổi Entity
        SaveConsumer->>Mongo: bulkOps.insert(entitiesToSave) [UNORDERED]
        alt Lỗi trùng khóa (DuplicateKey)
            Mongo-->>SaveConsumer: Warning (Tự động bỏ qua - Idempotent)
        else Lỗi nghiêm trọng
            SaveConsumer->>Kafka: Đẩy vào DLT (chat-messages-save-dlt)
        end
    end
```

---

## 2. Luồng Xác Thực, Cấp Phát Token & Thu Hồi Phiên (Token Versioning)

Sơ đồ mô tả quy trình đăng nhập, cơ chế lưu trữ JWT an toàn bằng HttpOnly Cookie, và tính năng **Single Sign-Out** tức thì thông qua trường `token_version`.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người Dùng
    participant Client as 🖥️ React Client
    participant AuthCtrl as 🎮 AuthController
    participant AuthService as ⚙️ AuthServiceImpl
    participant Postgres as 🐘 PostgreSQL
    participant Redis as ⚡ Redis
    participant JWT as 🔑 JwtService

    User->>Client: Nhập username / password
    Client->>AuthCtrl: POST /api/auth/login
    AuthCtrl->>AuthService: authenticate(request)
    AuthService->>Postgres: Truy vấn UserEntity theo username
    Postgres-->>AuthService: Trả về thông tin user (gồm password hash & tokenVersion)
    AuthService->>AuthService: BCrypt.verify(password, hash)
    
    rect rgb(255, 245, 245)
        note over AuthService, JWT: Sinh cặp Token kèm claim "v" = tokenVersion
        AuthService->>JWT: generateAccessToken(user, v=tokenVersion) [TTL=60 phút]
        AuthService->>JWT: generateRefreshToken(user, v=tokenVersion) [TTL=7 ngày]
    end

    AuthService-->>AuthCtrl: Đóng gói Token vào HttpOnly Cookies
    AuthCtrl-->>Client: Set-Cookie: access_token, refresh_token (SameSite=Strict)

    note over Client, AuthCtrl: Khi Người Dùng Đổi Mật Khẩu Hoặc Bấm "Đăng Xuất Mọi Thiết Bị"
    User->>Client: Thực hiện "Đăng xuất khỏi tất cả thiết bị"
    Client->>AuthCtrl: POST /api/auth/logout-all-devices
    AuthCtrl->>Postgres: UPDATE users SET token_version = token_version + 1 WHERE id = ?
    AuthCtrl->>Redis: Lưu access_token hiện tại vào blacklist:{token}
    AuthCtrl-->>Client: Thành công

    note over Client, AuthCtrl: Thiết Bị Cũ Khác Thực Hiện Request Tiếp Theo
    Client->>AuthCtrl: GET /api/users/profile (kèm access_token cũ có v=0)
    AuthCtrl->>JWT: Giải mã claim "v" từ token -> Trả về v=0
    AuthCtrl->>Postgres: Đọc token_version hiện tại của user trong DB -> Trả về v=1
    AuthCtrl->>AuthCtrl: So khớp: 0 != 1 (Mismatch Token Version!)
    AuthCtrl-->>Client: HTTP 401 Unauthorized (Token invalidated)
```

---

## 3. Luồng Quản Lý Hiện Diện & Cơ Chế Debounce 5 Giây (Presence Lifecycle)

Xử lý trạng thái người dùng (Online/Offline), đảm bảo khi người dùng tải lại trang (F5) không bị phát tín hiệu Offline giả tới bạn bè.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người Dùng (Client)
    participant WS as 🔌 WebSocket Interceptor
    participant Listener as 👂 WebSocketListener
    participant Redis as ⚡ Redis
    participant UserService as ⚙️ UserService
    participant FriendConsumer as 👥 Friend/Notification Consumer

    note over User, Redis: 1. Khi Người Dùng Mở Trang & Kết Nối WebSocket
    User->>WS: STOMP CONNECT /ws (kèm JWT Cookie)
    WS->>Listener: SessionConnectedEvent
    Listener->>Redis: HINCRBY online_users_count {username} 1
    Redis-->>Listener: Trả về count = 1 (Session đầu tiên)
    Listener->>Redis: ZADD online_users {epochTime} {username}
    Listener->>Redis: HINCRBY ws:routing:servers:{username} {serverId} 1
    
    alt Nếu count == 1 (Lần đầu Online)
        Listener->>UserService: setUserOnlineStatus(username, true)
        UserService->>Postgres: UPDATE users SET is_online = true
        UserService->>FriendConsumer: Broadcast thông báo "User A is Online" tới bạn bè
    end

    note over User, Redis: 2. Người Dùng Bấm F5 (Reload Trang)
    User->>WS: Ngắt kết nối socket cũ (DISCONNECT)
    WS->>Listener: SessionDisconnectEvent
    Listener->>Redis: HINCRBY online_users_count {username} -1
    Redis-->>Listener: Trả về count = 0
    Listener->>Listener: Phát hiện count <= 0. Bắt đầu đếm ngược DEBOUNCE 5 GIÂY!

    rect rgb(255, 250, 205)
        note over User, Listener: Trong vòng 5 giây: Trang web tải xong và kết nối lại!
        User->>WS: STOMP CONNECT (Socket mới)
        WS->>Listener: SessionConnectedEvent
        Listener->>Redis: HINCRBY online_users_count {username} 1
        Redis-->>Listener: Trả về count = 1
    end

    note over Listener, Redis: Hết thời gian 5 giây của Timer
    Listener->>Redis: HGET online_users_count {username}
    Redis-->>Listener: Trả về count = 1 (> 0)
    Listener->>Listener: Huỷ bỏ quy trình Offline (User chỉ vừa reload trang, không broadcast gì cả!)

    note over User, Redis: 3. Người Dùng Đóng Hẳn Trình Duyệt
    User->>WS: DISCONNECT
    Listener->>Redis: HINCRBY online_users_count {username} -1 -> count = 0
    Listener->>Listener: Bắt đầu Debounce 5 giây...
    note over Listener, Redis: Hết 5 giây: count vẫn bằng 0!
    Listener->>Redis: ZREM online_users {username}
    Listener->>Redis: HDEL online_users_count {username}
    Listener->>Redis: DEL ws:routing:servers:{username}
    Listener->>UserService: setUserOnlineStatus(username, false)
    UserService->>Postgres: UPDATE users SET is_online = false
    UserService->>FriendConsumer: Broadcast thông báo "User A is Offline" tới bạn bè
```

---

## 4. Luồng Trao Đổi Khóa & Mã Hóa Đầu-Cuối (E2EE Key Exchange)

Quy trình bảo mật đảm bảo Server không thể đọc được nội dung tin nhắn của người dùng.

```mermaid
sequenceDiagram
    autonumber
    actor Alice as 👩 Alice
    participant Server as ⚙️ ChatWeb Server & DB
    actor Bob as 👨 Bob

    note over Alice, Bob: Giai Đoạn 1: Đăng Ký Cặp Khóa Public / Private
    Alice->>Alice: Tự sinh cặp khóa RSA-2048 (RSA-OAEP)
    Alice->>Server: POST /api/keys/public-key (Gửi Alice_PublicKey)
    Server->>Server: Lưu Alice_PublicKey vào bảng `users`
    
    Bob->>Bob: Tự sinh cặp khóa RSA-2048
    Bob->>Server: POST /api/keys/public-key (Gửi Bob_PublicKey)
    Server->>Server: Lưu Bob_PublicKey vào bảng `users`

    note over Alice, Bob: Giai Đoạn 2: Alice Soạn & Mã Hóa Tin Nhắn Cho Bob
    Alice->>Server: GET /api/keys/public-key/Bob
    Server-->>Alice: Trả về chuỗi Bob_PublicKey
    
    Alice->>Alice: 1. Sinh ngẫu nhiên AES Session Key 256-bit (K_session)
    Alice->>Alice: 2. Mã hóa tin nhắn: AES-GCM(K_session, Plaintext) -> {Ciphertext, IV}
    Alice->>Alice: 3. Mã hóa K_session bằng Bob_PublicKey -> wrappedKeyRecipient
    Alice->>Alice: 4. Mã hóa K_session bằng Alice_PublicKey -> wrappedKeySender
    
    Alice->>Server: Gửi STOMP: { content: Ciphertext, iv: IV, wrappedKeyRecipient, wrappedKeySender }
    Server->>Server: Lưu Ciphertext & IV vào MongoDB (Server KHÔNG có khóa giải mã!)
    Server->>Bob: Đẩy qua WebSocket tới Bob

    note over Bob: Giai Đoạn 3: Bob Giải Mã Tin Nhắn
    Bob->>Bob: 1. Dùng Bob_PrivateKey giải mã wrappedKeyRecipient -> K_session
    Bob->>Bob: 2. Dùng K_session + IV giải mã Ciphertext -> Plaintext gốc ban đầu!
```
