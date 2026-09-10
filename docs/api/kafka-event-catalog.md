# Danh Mục Sự Kiện Kafka & Avro Schema (Kafka Event Catalog)

Tài liệu này tổng hợp toàn bộ các Topic, mô hình dữ liệu nhị phân (Avro Schema), cơ chế Producer - Consumer và chiến lược xử lý sự cố (Retry / Dead Letter Topic) trong cụm Kafka của ChatWeb.

---

## 1. Tổng Quan Cụm Kafka (Kafka Cluster Architecture)

- **Cấu hình cụm**: 2 Kafka Brokers chạy chế độ **KRaft (Kafka Raft Metadata)** không cần ZooKeeper ([docker-compose.yml](file:///home/phanhuukha/Dev/ChatWeb/docker-compose.yml#L48-L89)).
- **Quản lý Schema**: Confluent Schema Registry (Port `8081`) quản lý phiên bản Avro schemas và bảo đảm tính tương thích.
- **Serialization**: Apache Avro Serializer / Deserializer cho các topic có throughput cao (tin nhắn chat), và JSON Serializer cho các tác vụ sự kiện thông thường.

---

## 2. Bảng Danh Mục Các Kafka Topic (Topic Catalog)

| Tên Topic | Định dạng Payload | Consumer Groups | Trách nhiệm chính |
| :--- | :--- | :--- | :--- |
| `chat-messages` | **Apache Avro** (`ChatMessageAvro`) | 1. `chat-websocket-group`<br/>2. `chat-save-group` | Luồng xử lý tin nhắn chat thời gian thực: đẩy WebSocket nhanh (Fast-Push) và ghi đệm CSDL MongoDB (Write-Behind). |
| `chat-messages-save-dlt` | **Apache Avro** (`ChatMessageAvro`) | `chat-save-group-dlt` | Hàng đợi thư chết (DLT) cứu hộ các tin nhắn bị lỗi ghi MongoDB sau khi đã cạn số lần retry. |
| `chat-system-messages` | **JSON** (`SystemMessage`) | `system-websocket-group` | Phát sóng thông báo quản trị tới toàn bộ người dùng qua WebSocket `/topic/public`. |
| `message-update` | **JSON** (`UpdateMessagePayload`) | `message-update-group-id` | Xử lý các sự kiện sửa nội dung, thu hồi tin nhắn (xóa mềm), hoặc thả reaction emoji. |
| `email-messages` | **JSON** (`EmailEvent`) | `email-worker-group` | Worker ngầm gửi email xác thực OTP bất đồng bộ, chống nghẽn luồng đăng ký tài khoản. |
| `friend-notifications` | **JSON** (`FriendNotificationPayload`) | `friend-websocket-group` | Đẩy thông báo mời kết bạn hoặc chấp nhận kết bạn theo thời gian thực tới `/user/queue/notifications`. |

---

## 3. Chi Tiết Avro Schema: `ChatMessageAvro`

- **Tên Schema**: `ChatMessageAvro`
- **Namespace**: `com.web.backend.kafka.avro`
- **File định nghĩa**: [chatweb_be/src/main/resources/avro/ChatMessageAvro.avsc](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/resources/avro/ChatMessageAvro.avsc)

### Các trường dữ liệu (19 Fields):

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
    { "name": "reactions", "type": ["null", { "type": "map", "values": "string" }], "default": null }
  ]
}
```

*Lợi ích*: Dữ liệu được nén thành chuỗi nhị phân chuẩn hóa, tiết kiệm băng thông và tối ưu hiệu suất serialize/deserialize giữa Java backend và cụm Kafka.

---

## 4. Cơ Chế Xử Lý Lỗi & Tái Thử (Retry & DLT Resilience)

Để bảo đảm tính sẵn sàng cao và không bao giờ đánh mất tin nhắn của người dùng, hệ thống áp dụng chiến lược tái thử nghiêm ngặt tại các Consumer:

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
@KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-group-id}")
```
- Khi tiến trình đẩy WebSocket gặp sự cố mạng đột xuất, Consumer tự động retry tối đa 5 lần với khoảng nghỉ (backoff) 200ms trước khi ném ngoại lệ.

### 4.2. Khôi Phục Dữ Liệu Ngoại Tuyến (DLT & Write-Behind Bulk Ops)
Tại [DatabaseWriteBehindConsumer.java](file:///home/phanhuukha/Dev/ChatWeb/chatweb_be/src/main/java/com/web/backend/kafka/consumer/DatabaseWriteBehindConsumer.java):
1. **Ghi theo lô không tuần tự (Bulk Unordered Write)**:
   - Gom hàng loạt tin nhắn từ Kafka batch để thực thi `bulkOps.insert(entitiesToSave)` một lần duy nhất vào MongoDB.
2. **Tính lũy đẳng (Idempotent Write)**:
   - Nếu gặp lỗi trùng khóa (`DuplicateKeyException`), coi như tin nhắn đã được lưu an toàn và tiếp tục xử lý các phần tử khác trong batch.
3. **Cứu trợ đơn lẻ khi gặp lỗi (`BulkOperationException`)**:
   - Nếu cả lô gặp lỗi, hệ thống phân tích danh sách lỗi và tái thử ghi đơn lẻ từng phần tử thành công.
4. **Hàng đợi thư chết (`chat-messages-save-dlt`)**:
   - Mọi bản ghi thất bại vĩnh viễn sẽ chuyển tới topic DLT để một Consumer cứu hộ độc lập phục hồi và ghi đệm lại khi MongoDB hoạt động bình thường.
