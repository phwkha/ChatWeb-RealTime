# Danh Mục Sự Kiện Kafka & Avro Schema (Kafka Event Catalog)

Tài liệu này tổng hợp toàn bộ các Topic, mô hình dữ liệu nhị phân (Avro Schema), cơ chế Producer - Consumer và chiến lược xử lý sự cố (Retry / Dead Letter Topic) trong cụm Kafka của ChatWeb.

---

## 1. Tổng Quan Cụm Kafka (Kafka Cluster Architecture)

- **Cấu hình cụm**: 2 Kafka Brokers chạy chế độ **KRaft (Kafka Raft Metadata)** không cần ZooKeeper ([docker-compose.yml](file:///home/phanhuukha/Dev/ChatWeb/docker-compose.yml#L48-L89)).
- **Quản lý Schema**: Confluent Schema Registry (Port `8081`) quản lý phiên bản Avro schemas và bảo đảm tính tương thích ngược (Backward Compatibility).
- **Serialization**: Apache Avro Serializer / Deserializer cho các topic có throughput cao (tin nhắn chat), và JSON Serializer cho các tác vụ sự kiện thông thường.

---

## 2. Bảng Danh Mục Các Kafka Topic (Topic Catalog)

| Tên Topic | Định dạng Payload | Consumer Groups | Trách nhiệm chính |
| :--- | :--- | :--- | :--- |
| `chat-messages` | **Apache Avro** (`ChatMessageAvro`) | 1. `chat-websocket-group`<br/>2. `chat-save-group` | Luồng xử lý tin nhắn chat thời gian thực và ghi đệm CSDL MongoDB. |
| `chat-messages-save-dlt` | **Apache Avro** (`ChatMessageAvro`) | `chat-save-group-dlt` | Hàng đợi thư chết (DLT) cứu hộ các tin nhắn bị lỗi ghi MongoDB sau khi đã hết số lần retry. |
| `chat-system-messages` | **JSON** (`SystemMessage`) | `system-websocket-group-${uuid}` | Phát sóng thông báo quản trị tới toàn bộ người dùng qua WebSocket topic. |
| `message-update` | **JSON / Payload** | `message-update-group-id` | Xử lý các sự kiện sửa nội dung, xóa mềm, hoặc thả reaction emoji. |
| `email-messages` | **JSON** | `email-worker-group` | Worker ngầm gửi email xác thực OTP bất đồng bộ, chống nghẽn luồng đăng ký. |
| `friend-notifications` | **JSON** | `friend-websocket-group` | Đẩy thông báo mời kết bạn hoặc chấp nhận kết bạn theo thời gian thực. |

---

## 3. Chi Tiết Avro Schema: `ChatMessageAvro`

- **Tên Schema**: `ChatMessageAvro`
- **Namespace**: `com.web.backend.kafka.avro`
- **File định nghĩa**: [chatweb_be/src/main/resources/avro/ChatMessageAvro.avsc](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/resources/avro/ChatMessageAvro.avsc)

### Các trường dữ liệu (21 Fields):

```json
{
  "namespace": "com.web.backend.kafka.avro",
  "type": "record",
  "name": "ChatMessageAvro",
  "fields": [
    { "name": "id", "type": ["null", "string"], "default": null },
    { "name": "localId", "type": ["null", "string"], "default": null },
    { "name": "conversationId", "type": ["null", "string"], "default": null },
    { "name": "sender", "type": ["null", "string"], "default": null },
    { "name": "recipient", "type": ["null", "string"], "default": null },
    { "name": "content", "type": ["null", "string"], "default": null },
    { "name": "contentType", "type": ["null", "string"], "default": null },
    { "name": "messageType", "type": ["null", "string"], "default": null },
    { "name": "color", "type": ["null", "string"], "default": null },
    { "name": "replyToId", "type": ["null", "string"], "default": null },
    { "name": "fileUrl", "type": ["null", "string"], "default": null },
    { "name": "fileName", "type": ["null", "string"], "default": null },
    { "name": "fileSize", "type": ["null", "long"], "default": null },
    { "name": "timestamp", "type": ["null", "string"], "default": null },
    { "name": "status", "type": ["null", "string"], "default": null },
    { "name": "isEdited", "type": "boolean", "default": false },
    { "name": "isDeleted", "type": "boolean", "default": false },
    { "name": "isReacted", "type": "boolean", "default": false },
    { "name": "reactions", "type": ["null", { "type": "map", "values": "string" }], "default": null },
    { "name": "iv", "type": ["null", "string"], "default": null },
    { "name": "wrappedKeyRecipient", "type": ["null", "string"], "default": null },
    { "name": "wrappedKeySender", "type": ["null", "string"], "default": null }
  ]
}
```

*Lợi ích*: Dữ liệu được nén thành chuỗi nhị phân cực kỳ nhỏ gọn (giảm hơn 60% băng thông so với JSON thông thường), tốc độ serialize/deserialize vượt trội trên JVM.

---

## 4. Cơ Chế Xử Lý Lỗi & Tái Thử (Retry & DLT Resilience)

Để bảo đảm tính sẵn sàng cao và không bao giờ đánh mất tin nhắn của người dùng, hệ thống áp dụng chiến lược tái thử nghiệm ngặt tại các Consumer:

### 4.1. Cấu Hình Tự Động Thử Lại (`@RetryableTopic`)
Tại [ChatConsumer.java](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/kafka/consumer/ChatConsumer.java#L39-L40):
```java
@RetryableTopic(
    attempts = "5", 
    backoff = @Backoff(delay = 200), 
    sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, 
    dltStrategy = DltStrategy.NO_DLT, 
    autoCreateTopics = "true"
)
```
- Khi việc đẩy tin nhắn qua WebSocket gặp lỗi tạm thời (ví dụ do quá tải luồng CPU), hệ thống sẽ thử lại tối đa 5 lần, mỗi lần cách nhau 200ms.

### 4.2. Xử Lý Ghi Hàng Loạt & Cứu Hộ DLT (`DatabaseWriteBehindConsumer`)
Tại [DatabaseWriteBehindConsumer.java](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/kafka/consumer/DatabaseWriteBehindConsumer.java):
1. **Ghi theo lô không tuần tự (Unordered Bulk Operations)**:
   - Gom hàng trăm tin nhắn vào một batch và gửi 1 lệnh ghi duy nhất xuống MongoDB.
2. **Tính lũy đẳng (Idempotent Write)**:
   - Nếu trong lô có tin nhắn bị trùng lặp ID (`DuplicateKeyException` mã 11000), hệ thống tự động bỏ qua lỗi và tiếp tục lưu các tin nhắn hợp lệ khác mà không làm gián đoạn toàn bộ lô.
3. **Dead Letter Topic Consumer (`chat-messages-save-dlt`)**:
   - Các tin nhắn thất bại sau toàn bộ các chu kỳ retry sẽ được Kafka tự động đẩy vào topic `-save-dlt`.
   - Một consumer chuyên trách (`handleDltPersistence`) sẽ đọc từ topic này và lưu trữ riêng lẻ từng tin nhắn kèm log cảnh báo chi tiết, đảm bảo 100% dữ liệu không bị thất thoát.
