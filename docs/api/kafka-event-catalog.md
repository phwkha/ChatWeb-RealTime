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

| Topic Name | Serialization | Consumer Group ID | Concurrency | Primary Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| **`chat-messages`** | **Apache Avro** (`ChatMessageAvro`) | 1. `chat-websocket-group`<br/>2. `chat-save-group` | 4 (Realtime)<br/>2 (Batch Save) | Core chat stream: Fast WebSocket push delivery and bulk Write-Behind persistence into MongoDB. |
| **`chat-messages-save-dlt`**| **Apache Avro** (`ChatMessageAvro`) | `chat-save-group-dlt` | 2 | Dead Letter Topic (DLT) holding records that failed MongoDB batch insertion after maximum retries. |
| **`chat-system-messages`** | **JSON** (`SystemMessage`) | `system-websocket-group` | 2 | Broadcasts administrative system announcements to connected users via `/topic/public`. |
| **`message-update`** | **JSON** (`UpdateMessagePayload`) | `message-update-group-id` | 2 | Dispatches message edits, soft-deletions, emoji reactions, and watermark read receipt notifications. |
| **`email-messages`** | **JSON** (`EmailEvent`) | `email-worker-group` | 1 | Asynchronously delivers verification OTPs and password reset emails without blocking web requests. |
| **`friend-notifications`**| **JSON** (`FriendNotificationPayload`) | `friend-websocket-group` | 2 | Pushes real-time friend invitation and acceptance notifications to `/user/queue/notifications`. |

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
    { "name": "reactions", "type": ["null", { "type": "map", "values": "string" }], "default": null }
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
public void listen(ConsumerRecord<String, ChatMessageAvro> record) { ... }
```
- In the event of transient network hiccups or temporary socket routing delays, the consumer automatically retries up to 5 times with a 200ms fixed backoff before surfacing an error.

### 4.2. Database Write-Behind Batching & DLT Strategy
Configured in `KafkaConfig.java` and executed in `DatabaseWriteBehindConsumer.java`:
1. **Unordered Bulk Insert**:
   - Gathers batches of up to 200 records per poll (max wait 500ms) and executes an unordered bulk insert into MongoDB:
     ```java
     BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
     bulkOps.insert(entitiesToSave);
     bulkOps.execute();
     ```
2. **Duplicate Key Tolerance**:
   - Catches `DuplicateKeyException` to guarantee idempotent execution: already-persisted records do not abort the remainder of the batch.
3. **Dead Letter Recovery**:
   - If an unrecoverable failure occurs (e.g. malformed database document), `DeadLetterPublishingRecoverer` transfers the failed record to `chat-messages-save-dlt` after 4 retries with a 500ms backoff.
   - Non-retryable exceptions (e.g., `SerializationException`, `RecordTooLargeException`) are routed directly to the DLT without redundant retries.
