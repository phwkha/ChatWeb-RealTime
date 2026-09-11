# Đặc Tả Giao Thức WebSocket & STOMP (WebSocket & STOMP Protocol Spec)

Tài liệu này đặc tả toàn bộ giao diện truyền thông thời gian thực qua giao thức **STOMP over WebSocket** trong hệ thống ChatWeb.

---

## 1. Kết Nối & Bắt Tay (Handshake & Authentication)

Hệ thống cung cấp điểm kết nối WebSocket chuẩn hỗ trợ fallback SockJS cho các trình duyệt hoặc mạng chặn giao thức WS thuần.

- **WebSocket URL**:
  - Phát triển cục bộ: `ws://localhost:8080/ws` (hoặc qua SockJS: `http://localhost:8080/ws`)
  - Nginx Reverse Proxy: `wss://<domain>/ws`
- **Cơ chế xác thực (Authentication)**:
  1. **Ưu tiên 1 (Cookie)**: Tự động trích xuất từ Cookie `accessToken` (được bọc vào session attribute `jwt_token_cookie`) đi kèm trong request bắt tay HTTP Upgrade.
  2. **Ưu tiên 2 (STOMP Header)**: Truyền qua header `Authorization` khi gửi frame STOMP `CONNECT`:
     ```stomp
     CONNECT
     accept-version:1.2,1.1,1.0
     heart-beat:10000,10000
     Authorization:Bearer <access_token>
     Accept-Language:vi-VN
     \0
     ```
- **Xác thực bảo mật đa tầng tại [WebSocketConfig.java](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/config/WebSocketConfig.java)**:
  - **Blacklist Check**: Kiểm tra token có nằm trong Redis key `blacklist:<token>` hay không.
  - **Token Version Check**: Giải mã claim `v` trong JWT và so sánh với `token_version` trong PostgreSQL. Nếu không khớp $\rightarrow$ Từ chối kết nối ngay lập tức (thu hồi phiên).
  - **I18n Locale Resolution**: Thiết lập ngôn ngữ phản hồi theo header `Accept-Language`.

---

## 2. Quy Ước Tiền Tố Định Tuyến (Destination Prefixes)

Spring Boot WebSocket Broker được cấu hình phân tách rõ ràng 3 không gian địa chỉ:

| Tiền tố (Prefix) | Loại hình (Type) | Mục đích sử dụng |
| :--- | :--- | :--- |
| **`/app`** | Application Destination | Điểm nhận dữ liệu từ Client gửi lên Controller để xử lý logic. |
| **`/topic`** | Broadcast Broker | Kênh phát thanh công khai một-nhiều (One-to-Many / Pub-Sub). |
| **`/user` / `/queue`** | Point-to-Point Broker | Kênh gửi dữ liệu riêng tư một-một (One-to-One) tới một tài khoản cụ thể. |

---

## 3. Danh Sách Inbound Endpoints (Client $\rightarrow$ Server)

### 3.1. Gửi Tin Nhắn Riêng Tư 1-1 (Private Message & Typing)
- **STOMP Destination**: `/app/chat/sendPrivateMessage`
- **Quyền hạn**: Người dùng đã xác thực (Authenticated User). Phải là bạn bè của người nhận (kiểm tra quan hệ bạn bè trong CSDL).
- **Payload Request** ([ChatMessageRequest](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/controller/request/ChatMessageRequest.java)):

```json
{
  "localId": "uuid-v4-client-generated-12345",
  "recipient": "bob_username",
  "content": "Xin chào Bob, bạn khỏe không?",
  "contentType": "TEXT",
  "messageType": "CHAT",
  "color": "#3B82F6",
  "replyToId": "65e52a8c1f938b29c8e1a123",
  "fileUrl": null,
  "fileName": null,
  "fileSize": null
}
```

*Lưu ý nghiệp vụ*:
- `localId`: Client tự sinh UUID để Backend thực hiện kiểm tra `SETNX` với key `ws:dedup:{sender}:{localId}` chống nhận tin nhắn trùng lặp khi mạng bị chập chờn.
- `messageType`:
  - `CHAT`: Tin nhắn trò chuyện thông thường.
  - `TYPING`: Thông báo người gửi đang nhập văn bản.
- `contentType`: `TEXT`, `IMAGE`, `VIDEO`, `FILE`.

---

### 3.2. Gửi Thông Báo Hệ Thống (System Announcement)
- **STOMP Destination**: `/app/chat/sendMessageSystem`
- **Quyền hạn**: Chỉ dành cho Admin có quyền `@PreAuthorize("hasAuthority('ADMIN_SEND-MESSAGE')")`.
- **Payload Request** ([MessageSystemRequest](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/controller/request/MessageSystemRequest.java)):

```json
{
  "content": "Hệ thống sẽ bảo trì nâng cấp cụm máy chủ vào lúc 02:00 sáng mai.",
  "survivalTime": 86400
}
```
*`survivalTime`*: Thời gian tồn tại của thông báo tính bằng giây. Sau thời gian này, MongoDB TTL Index sẽ tự động hủy thông báo.

---

## 4. Danh Sách Outbound Destinations (Server $\rightarrow$ Client)

### 4.1. Kênh Nhận Tin Nhắn Riêng Tư & Phản Hồi (ACK)
- **Client Subscription**: `/user/queue/messages`
- **Mô tả**: Khi có người gửi tin nhắn cho bạn, hoặc server gửi lại bản tin xác nhận đã nhận (ACK chứa `localId` để client cập nhật trạng thái tin nhắn đã gửi thành công).
- **Payload Response** ([ChatMessageResponse](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/controller/response/ChatMessageResponse.java)):

```json
{
  "id": "65e52b121f938b29c8e1a456",
  "localId": "uuid-v4-client-generated-12345",
  "conversationId": "alice_bob",
  "sender": "alice_username",
  "recipient": "bob_username",
  "content": "Xin chào Bob, bạn khỏe không?",
  "contentType": "TEXT",
  "messageType": "CHAT",
  "color": "#3B82F6",
  "replyToId": null,
  "fileUrl": null,
  "fileName": null,
  "fileSize": null,
  "timestamp": "2026-09-03T07:45:00.120Z",
  "status": "SENT",
  "isEdited": false,
  "isDeleted": false,
  "isReacted": false,
  "reactions": null
}
```

---

### 4.2. Kênh Nhận Thông Báo Lời Mời Kết Bạn (Friend Notifications)
- **Client Subscription**: `/user/queue/notifications`
- **Mô tả**: Nhận thông báo thời gian thực khi có người gửi lời mời kết bạn hoặc chấp nhận lời mời từ bạn.

---

### 4.3. Kênh Nhận Thông Báo Toàn Hệ Thống (Public Broadcast)
- **Client Subscription**: `/topic/public`
- **Mô tả**: Toàn bộ người dùng đang trực tuyến đều nhận được thông báo chung từ ban quản trị.
- **Payload Response** ([MessageSystemResponse](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/controller/response/MessageSystemResponse.java)):

```json
{
  "id": "65e52c901f938b29c8e1a789",
  "sender": "admin_system",
  "content": "Hệ thống sẽ bảo trì nâng cấp cụm máy chủ vào lúc 02:00 sáng mai.",
  "timestamp": "2026-09-03T07:46:15.000Z"
}
```

---

### 4.4. Kênh Báo Lỗi WebSocket Cá Nhân
- **Client Subscription**: `/user/queue/errors`
- **Mô tả**: Nhận thông báo lỗi xử lý socket khi gửi tin thất bại (ví dụ vi phạm rate limit hoặc gửi tin cho người chưa kết bạn).

---

## 5. Xử Lý Lỗi WebSocket Chuẩn Hóa (STOMP Error Handling)

Khi xảy ra lỗi trong quá trình xử lý STOMP frame, hệ thống sử dụng [StompSubProtocolErrorHandler](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/config/WebSocketConfig.java#L77-L100) để tạo frame `ERROR` với payload JSON [ErrorSocketResponse](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/controller/response/ErrorSocketResponse.java):

```json
{
  "code": 400,
  "errorCode": "STOMP_ERROR",
  "message": "Hai người chưa phải là bạn bè, không thể gửi tin nhắn.",
  "request": null
}
```
Client bắt frame STOMP `ERROR` hoặc tin nhắn từ `/user/queue/errors` để hiển thị Toast thông báo lỗi tới người dùng.
