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
    Nginx->>WSInterceptor: Chuyển tiếp kết nối WebSocket
    WSInterceptor->>WSInterceptor: Kiểm tra JWT Token & Blacklist trong Redis
    WSInterceptor->>Controller: Chuyển tiếp message hợp lệ
    Controller->>Service: sendPrivateMessage(sender, request)
    
    rect rgb(240, 248, 255)
        note over Service, Redis: Bước 1: Khử trùng lặp & Giới hạn tốc độ
        Service->>Redis: SETNX ws:dedup:{sender}:{localId} (TTL=300s)
        alt Đã tồn tại key (Duplicate packet do lag)
            Redis-->>Service: Return FALSE
            Service-->>Sender: Âm thầm bỏ qua
        else Key mới hợp lệ
            Redis-->>Service: Return TRUE
        end
        Service->>Service: Kiểm tra quan hệ bạn bè & trạng thái tài khoản
    end

    rect rgb(255, 250, 240)
        note over Service, Redis: Bước 2: Caching tin nhắn gần nhất
        Service->>Redis: Lưu vào chat:recent:hash & chat:recent:zset
    end

    rect rgb(240, 255, 240)
        note over Service, Kafka: Bước 3: Phát tán sự kiện nhị phân
        Service->>Kafka: Publish ChatMessageAvro vào topic "chat-messages"
    end

    par Nhánh 1: Fast-Push (Độ trễ thấp tới người nhận)
        Kafka->>ChatConsumer: Consume Avro payload
        ChatConsumer->>WSRouting: routeMessage(recipient, /queue/messages)
        WSRouting->>Redis: Tra cứu Node ID tại ws:routing:servers:{recipient}
        alt Recipient cùng Node
            WSRouting->>Recipient: Đẩy STOMP frame trực tiếp
        else Recipient ở Node khác
            WSRouting->>Redis: Publish channel:server:{targetServerId}
            Redis->>Recipient: Node đích nhận Pub/Sub và đẩy tới Client B
        end
        ChatConsumer->>WSRouting: routeMessage(sender, /queue/messages) [Báo ACK gửi thành công]
        WSRouting->>Sender: Nhận ACK cập nhật trạng thái tin nhắn
    and Nhánh 2: Write-Behind (Ghi gom lô xuống CSDL)
        Kafka->>SaveConsumer: Consume danh sách tin nhắn theo batch
        SaveConsumer->>Mongo: Bulk Write (insert không tuần tự vào messages)
        alt Gặp lỗi DuplicateKeyException
            Mongo-->>SaveConsumer: Warning (Tự động bỏ qua - Idempotent)
        else Gặp lỗi hệ thống nghiêm trọng
            SaveConsumer->>Kafka: Chuyển bản ghi sang topic "chat-messages-save-dlt"
        end
    end
```

---

## 2. Luồng Xác Thực, Cấp Phát Token & Single Sign-Out (`token_version`)

Sơ đồ mô tả quy trình đăng nhập, cấp phát Cookie bảo mật và cơ chế thu hồi phiên tức thì trên toàn bộ thiết bị.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng
    participant Nginx as 🛡️ Nginx Ingress
    participant AuthCtrl as 🎮 AuthController
    participant AuthSvc as ⚙️ AuthenticationService
    participant Cuckoo as 🔍 CuckooFilterService
    participant DB as 🐘 PostgreSQL
    participant Redis as ⚡ Redis
    participant JWT as 🔑 JwtService

    User->>Nginx: POST /api/auth/login { username, password }
    Nginx->>AuthCtrl: Forward request
    AuthCtrl->>AuthSvc: authenticate(request)
    
    AuthSvc->>Cuckoo: exists("filter:usernames", username)
    alt Không tồn tại trong Cuckoo Filter
        Cuckoo-->>AuthSvc: False (Tài khoản không tồn tại, chặn ngay)
        AuthSvc-->>User: 404 Not Found
    else Có thể tồn tại
        Cuckoo-->>AuthSvc: True
        AuthSvc->>DB: Truy vấn UserEntity theo username
    end

    AuthSvc->>AuthSvc: Đối chiếu mật khẩu BCrypt
    AuthSvc->>JWT: Sinh Access Token (claim v = user.token_version, exp = 60m)
    AuthSvc->>JWT: Sinh Refresh Token (exp = 7 ngày)
    
    AuthSvc-->>AuthCtrl: Trả về Token Pair
    AuthCtrl-->>User: Set-Cookie: jwt_token_cookie (HttpOnly, SameSite=Strict)

    note over User, DB: Khi người dùng chọn "Đăng xuất khỏi tất cả thiết bị"
    User->>AuthCtrl: POST /api/auth/logout-all-devices
    AuthCtrl->>AuthSvc: logoutAllDevices(username)
    AuthSvc->>DB: UPDATE users SET token_version = token_version + 1 WHERE id = ?
    AuthSvc->>Redis: Đưa Access Token hiện tại vào blacklist:{token}
    AuthSvc-->>User: 200 OK (Toàn bộ token cũ mang version cũ lập tức bị vô hiệu hóa)
```

---

## 3. Luồng Quản Lý Trạng Thái Hiện Diện (Presence Lifecycle & Debouncing)

Cơ chế chống nhiễu Flapping khi người dùng reload trình duyệt hoặc mạng chuyển tiếp nhanh.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 👤 Client Trình Duyệt
    participant WS as 🔌 WebSocketListener
    participant Redis as ⚡ Redis
    participant DB as 🐘 PostgreSQL
    participant Scheduler as ⏱️ ScheduledExecutorService

    note over Client, Redis: Giai đoạn 1: Kết nối mở mới (CONNECT)
    Client->>WS: Frame CONNECT thành công
    WS->>Redis: HINCRBY online_users_count {username} 1
    WS->>Redis: ZADD online_users {score = timestamp} {username}
    alt count == 1 (Lần đầu mở ứng dụng)
        WS->>DB: UPDATE users SET is_online = true WHERE username = ?
        WS->>WS: Broadcast thông báo bạn bè: User Online
    else count > 1 (Mở thêm tab mới)
        WS->>WS: Giữ nguyên trạng thái (Không phát lặp thông báo)
    end

    note over Client, Redis: Giai đoạn 2: Ngắt kết nối (F5 Reload trang)
    Client->>WS: Frame DISCONNECT (Tab cũ đóng lại)
    WS->>Redis: HINCRBY online_users_count {username} -1
    alt count <= 0 (Không còn tab nào mở)
        WS->>Scheduler: Lên lịch hẹn kiểm tra lại sau 5 giây: processOfflineDebounce()
    end

    note over Client, Redis: Giai đoạn 3: Kết nối lại trước khi hết 5 giây (F5 xong)
    Client->>WS: Frame CONNECT mới (Trang web load xong)
    WS->>Redis: HINCRBY online_users_count {username} 1 (count trở lại >= 1)

    note over Scheduler, DB: Hết 5 giây: Bộ đếm thực thi
    Scheduler->>Redis: HGET online_users_count {username}
    alt count > 0 (Người dùng đã quay trở lại!)
        Scheduler->>Scheduler: Hủy bỏ sự kiện Offline (Triệt tiêu rung lắc mạng)
    else count <= 0 (Người dùng thực sự đã đóng ứng dụng)
        Scheduler->>DB: UPDATE users SET is_online = false WHERE username = ?
        Scheduler->>Redis: ZREM online_users {username}
        Scheduler->>WS: Broadcast thông báo bạn bè: User Offline
    end
```

---

## 4. Luồng Lời Mời Kết Bạn & Thông Báo Thời Gian Thực (Friend Request & Notification Pipeline)

Sơ đồ mô tả quy trình gửi và chấp nhận lời mời kết bạn với cơ chế bảo đảm tính lũy đẳng (Idempotency) và thông báo đẩy thời gian thực qua Kafka và WebSocket.

```mermaid
sequenceDiagram
    autonumber
    actor Alice as 👤 Alice (Sender)
    participant FriendCtrl as 🎮 FriendController
    participant FriendSvc as ⚙️ FriendServiceImpl
    participant Redis as ⚡ Redis (Idempotency & Cache)
    participant DB as 🐘 PostgreSQL
    participant Kafka as 📨 Kafka (friend-notifications)
    participant FriendConsumer as 🚀 FriendConsumer
    participant WSRouting as 🧭 WebSocketRoutingService
    actor Bob as 👥 Bob (Recipient)

    Alice->>FriendCtrl: POST /api/friends/request { targetUsername: "bob" }
    FriendCtrl->>FriendSvc: sendFriendRequest("alice", "bob")
    FriendSvc->>DB: Kiểm tra chưa bị block và chưa tồn tại quan hệ
    FriendSvc->>DB: INSERT INTO friendships (requester, addressee, status='PENDING')
    FriendSvc->>Kafka: Publish FriendNotificationPayload vào topic "friend-notifications"
    FriendSvc-->>Alice: 200 OK (Gửi lời mời thành công)

    Kafka->>FriendConsumer: Consume friend notification
    FriendConsumer->>WSRouting: routeMessage("bob", "/queue/notifications", payload)
    WSRouting->>Bob: Gửi STOMP frame tới /user/queue/notifications (Bob thấy thông báo đỏ ngay)

    note over Bob, Alice: Bob nhấn "Đồng ý kết bạn" (Kèm X-Idempotency-Key)
    Bob->>FriendCtrl: POST /api/friends/accept { targetUsername: "alice" }<br/>Header: X-Idempotency-Key: <UUID>
    FriendCtrl->>Redis: Kiểm tra & khóa idempotent:friend_accept:<UUID> (TTL 300s)
    FriendCtrl->>FriendSvc: acceptFriendRequest("bob", "alice")
    FriendSvc->>DB: UPDATE friendships SET status='ACCEPTED'
    FriendSvc->>Kafka: Publish sự kiện ACCEPTED vào topic "friend-notifications"
    FriendCtrl-->>Bob: 200 OK

    Kafka->>FriendConsumer: Consume friend notification
    FriendConsumer->>WSRouting: routeMessage("alice", "/queue/notifications", payload)
    WSRouting->>Alice: Gửi STOMP frame báo Alice: Bob đã đồng ý kết bạn!
```
