# Kafka Event Catalog & Avro Specifications

This document catalogs all Apache Kafka topics, binary serialization contracts (**Apache Avro**), consumer group topologies, and resilience strategies (retries and Dead Letter Topics) in ChatWeb.

---

## 1. Kafka Cluster Architecture

- **Cluster Topology**: 2 Kafka Brokers configured in **KRaft (Kafka Raft Metadata)** mode, eliminating ZooKeeper dependencies.
- **Schema Management**: Confluent Schema Registry (Port `8081`) validates schema evolution and manages binary serialization contracts.
- **Producer Configuration Defaults**:
  - `enable.idempotence = true`: Enforces exactly-once semantic delivery from Spring Boot producers to broker partitions.
  - `acks = all`: Requires full acknowledgment across all in-sync replicas (ISR) before considering an event committed.
  - `compression.type = snappy`: High-performance binary compression minimizing network overhead.
- **Consumer Configuration Defaults**:
  - `partition.assignment.strategy = CooperativeStickyAssignor`: Enables incremental cooperative rebalancing, preventing stop-the-world pauses during consumer node scaling.
  - `session.timeout.ms = 45000` & `heartbeat.interval.ms = 15000`.

---

## 2. Kafka Topic Catalog

| Topic Name | Partitions | Replicas | Serialization | Consumer Group ID | Concurrency | Primary Responsibility |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`chat-messages`** | 12 | 2 | **Apache Avro** (`ChatMessageAvro`) | 1. `chat-websocket-group`<br/>2. `chat-save-group` | 4 (Realtime)<br/>2 (Batch Save) | Core chat stream: Transports full message lifecycle (`CREATE`, `EDIT`, `REVOKE`, `REACT`) for sub-10ms WebSocket push and batch Write-Behind persistence into MongoDB. |
| **`chat-messages-save-dlt`**| 12 | 2 | **Apache Avro** (`ChatMessageAvro`) | `chat-save-group-dlt` | 2 | Dead Letter Topic (DLT) holding records that failed MongoDB persistence; processed with container-level backoff retry. |
| **`chat-system-messages`** | 6 | 2 | **JSON** (`SystemMessage`) | `system-websocket-group-${random.uuid}` (Dynamic fan-out per node) | 2 | Broadcasts both administrative announcements (`ANNOUNCEMENT`) and user world chat messages (`WORLD_CHAT`) across cluster nodes to `/topic/public`. |
| **`message-update`** | 6 | 2 | **JSON** (`UpdateMessagePayload`) | `message-update-group-id` | 2 | Exclusively dispatches conversation read watermark sync events (`UpdateMessageType.STATUS` / `ReadReceiptResponse`). |
| **`email-messages`** | 3 | 2 | **JSON** (`EmailPayload`) | `email-worker-group` | 1 | Asynchronously delivers registration and password-reset verification OTPs. *(Note: Email/phone change OTP in `UserServiceImpl` and admin broadcasts in `AdminEmailController` dispatch synchronously via `EmailService`).* |
| **`friend-notifications`**| 6 | 2 | **JSON** (`FriendPayload`) | `friend-websocket-group` | 2 | Pushes real-time friend invitation and acceptance notifications to `/user/queue/notifications`. |

---

## 3. Avro Schema Definition: `ChatMessageAvro`

- **Schema Name**: `ChatMessageAvro`
- **Namespace**: `com.web.backend.kafka.avro`
- **Source File**: `chatweb_be/src/main/resources/avro/ChatMessageAvro.avsc`

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
    { "name": "actionType", "type": ["null", "string"], "default": null },
    { "name": "notificationId", "type": ["null", "long"], "default": null },
    { "name": "actionBy", "type": ["null", "string"], "default": null }
  ]
}
```

*Binary Serialization Advantage*: Using Apache Avro with Confluent Schema Registry compresses payload sizes up to 70% compared to raw JSON strings, maximizing Kafka broker throughput.

---

## 4. Resilience & Fault Tolerance Strategies

### 4.1. Fast-Push Consumer Resilience (`@RetryableTopic`)
Implemented in `ChatConsumer.java`:
```java
@RetryableTopic(
    attempts = "5",
    backoff = @Backoff(delay = 200),
    sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
    dltStrategy = DltStrategy.NO_DLT,
    autoCreateTopics = "true"
)
@KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-group-id}")
public void listenChatMessages(
    @Payload ChatMessageAvro message,
    @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String conversationKey) { ... }
```
- In the event of transient network hiccups or temporary socket routing delays, the consumer automatically retries up to 5 times with a 200ms fixed backoff before surfacing an error.

### 4.1b. Consumer-Side Idempotency via `ConsumerDeduplicationService`
To guard against duplicated message delivery caused by consumer group rebalances or producer retries, consumers (`ChatConsumer`, `FriendConsumer`, `UpdateMessageConsumer`, and `EmailConsumer`) invoke `ConsumerDeduplicationService`:
- **Redis Dedup Key**: `kafka:dedup:{consumerName}:{eventId}`
- **TTL Strategy**:
  - `10 minutes`: Real-time push consumers (`chat_push`, `friend_push`, `message_push_update`).
  - `24 hours`: Asynchronous email worker (`email`) operating under `AckMode.MANUAL_IMMEDIATE`.
- **Fault Recovery (`clearOnFailure`)**: If downstream processing encounters an unrecoverable exception, `clearOnFailure()` immediately evicts the Redis deduplication key so that retry attempts or re-deliveries are not prematurely dropped.

### 4.2. Database Write-Behind Batching & DLT Strategy
Configured in `KafkaConfig.java` and executed in `DatabaseWriteBehindConsumer.java`:

1. **Unordered Bulk Insert & Upsert**:
   - Gathers batches of up to 200 records per poll (max wait 500ms) and executes bulk operations against MongoDB collection `messages` using `BulkOperations.BulkMode.UNORDERED`:
     - **`CREATE`**: `bulkOps.insert(entity)`
     - **`EDIT`**: `bulkOps.upsert(query, buildEditUpdate(avro))`
     - **`REVOKE`**: `bulkOps.upsert(query, buildRevokeUpdate(avro))` (wipes content, unsets attachments/reactions, sets `isDeleted = true`)
     - **`REACT`**: `bulkOps.upsert(query, buildReactUpdate(avro))`
     ```java
     BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
     // append inserts/upserts according to avro.getActionType()
     bulkOps.execute();
     ```

2. **Accurate Bulk Operation Error Handling (`retryBulkFailuresIndividually`)**:
   - In MongoDB `BulkOperations.BulkMode.UNORDERED`, all non-failing operations in the batch succeed and commit independently.
   - When a `BulkOperationException` occurs, `retryBulkFailuresIndividually(payloadsToSave, boe)` iterates strictly through `boe.getErrors()`.
   - For each failed element:
     - Ignores duplicate key errors (`code == 11000`) since the document is already safely committed.
     - Retries *only the specific failed operations* individually by looking up their payload index via `error.getIndex()`. It does **not** re-execute already committed batch elements.

3. **DLT Persistence Consumer & Container-Level Retry**:
   - Fatal or persistent database write failures transfer the message payload to `chat-messages-save-dlt`.
   - `DatabaseWriteBehindConsumer.handleDltPersistence()` listens on `chat-messages-save-dlt` with group `chat-save-group-dlt`.
   - The DLT listener uses the container factory `dltChatAvroListenerContainerFactory` configured with `FixedBackOff(5000L, 5L)` (retries up to 5 times with a 5-second interval) to replay and persist failed documents into MongoDB once database health is restored.
