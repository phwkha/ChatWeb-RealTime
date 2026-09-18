# WebSocket & STOMP Protocol Specification

This document specifies the real-time full-duplex communication interface built upon the **STOMP (Simple Text Oriented Messaging Protocol) over WebSocket** architecture within ChatWeb.

---

## 1. Connection Handshake & Authentication

The messaging gateway exposes a standardized WebSocket endpoint with transparent **SockJS fallback** support for restrictive proxies and legacy browsers.

- **WebSocket URLs**:
  - Direct / Local Development: `ws://localhost/ws` (or SockJS HTTP fallback: `http://localhost/ws`)
  - Reverse Proxy (Production / Staging): `wss://<domain>/ws`
- **Authentication Lifecycle**:
  The system supports dual token transmission strategies during the initial STOMP handshake:
  1. **Primary Strategy (STOMP Header)**: The client attaches the JWT Access Token directly within the `CONNECT` frame:
     ```stomp
     CONNECT
     accept-version:1.2,1.1,1.0
     heart-beat:10000,10000
     Authorization:Bearer <access_token>
     Accept-Language:en-US
     \0
     ```
  2. **Secondary Strategy (HttpOnly Cookie Fallback)**: The handshake interceptor extracts the `accessToken` cookie from the initial HTTP Upgrade request attributes.

### Handshake Interceptor Security Checks
Implemented in `WebSocketConfig.java` / `ChannelInterceptor`:
- **Redis Token Blacklist**: Verifies the token has not been revoked (`blacklist:<token>`).
- **Token Version Enforcement**: Extracts claim `v` from the JWT and compares it against the user's current `token_version` in PostgreSQL. If mismatched, the connection is instantly rejected, terminating sessions across all revoked devices.
- **Locale Resolution**: Configures localized socket responses based on the client's `Accept-Language` header.

---

## 2. Destination Prefix Conventions

Spring Boot WebSocket message broker partitions destination paths into three distinct scopes:

| Prefix | Type | Description |
| :--- | :--- | :--- |
| **`/app`** | Application Destination | Targets Spring `@MessageMapping` controller endpoints for business logic processing. |
| **`/topic`** | Broadcast Broker | One-to-many public Pub/Sub channels delivered to all subscribed online clients. |
| **`/user` / `/queue`** | Point-to-Point Broker | One-to-one private queues addressed to specific authenticated usernames. |

---

## 3. Inbound Endpoints (Client $\rightarrow$ Server)

### 3.1. Send Private Message (1-to-1 Chat & Typing Indicator)
- **STOMP Destination**: `/app/chat/sendPrivateMessage`
- **Authorization**: Authenticated user. Sender and recipient must have an `ACCEPTED` friendship record.
- **Request Payload (`ChatMessageRequest`)**:

```json
{
  "localId": "b1f8b417-742a-43cf-bb15-090c2a7df641",
  "recipient": "bob_smith",
  "content": "Hello Bob, are we still meeting today?",
  "contentType": "TEXT",
  "messageType": "CHAT",
  "color": "#3B82F6",
  "replyToId": "65e52a8c1f938b29c8e1a123",
  "fileUrl": null,
  "fileName": null,
  "fileSize": null
}
```

#### Field Specifications:
- `localId` (*String, Required*): Client-generated UUID v4 used for backend deduplication (`SETNX ws:dedup:{sender}:{localId}` with TTL 300s).
- `recipient` (*String, Required*): Username of the intended recipient.
- `content` (*String, Optional*): Text content. Required when `contentType == 'TEXT'`.
- `contentType` (*Enum*): `TEXT`, `IMAGE`, `VIDEO`, `FILE`.
- `messageType` (*Enum*):
  - `CHAT`: Standard chat message.
  - `TYPING`: Transient typing indicator event (not persisted to MongoDB).
- `color` (*String, Optional*): UI bubble accent color hex code.
- `replyToId` (*String, Optional*): MongoDB `_id` of a quoted parent message.
- `fileUrl` (*String, Optional*): Direct Cloudinary CDN URL for media payloads.
- `fileName` / `fileSize` (*Optional*): Metadata for attachments.

---

### 3.2. Broadcast System Announcement
- **STOMP Destination**: `/app/chat/sendMessageSystem`
- **Authorization**: Administrative users with authority `@PreAuthorize("hasAuthority('ADMIN_SEND-MESSAGE')")`.
- **Request Payload (`MessageSystemRequest`)**:

```json
{
  "content": "Scheduled server maintenance will take place tonight at 02:00 UTC.",
  "survivalTime": 86400
}
```

- `survivalTime` (*Long, Required*): Lifetime of the announcement in seconds. The backend computes `expiresAt = now + survivalTime`, allowing MongoDB TTL index to automatically purge the document upon expiration.

---

## 4. Outbound Destinations (Server $\rightarrow$ Client)

### 4.1. Private Messages & Delivery Acknowledgment (ACK)
- **Client Subscription**: `/user/queue/messages`
- **Payload Model (`ChatMessageResponse`)**:

```json
{
  "id": "65e52b121f938b29c8e1a456",
  "localId": "b1f8b417-742a-43cf-bb15-090c2a7df641",
  "conversationId": "alice_bob",
  "sender": "alice_smith",
  "recipient": "bob_smith",
  "content": "Hello Bob, are we still meeting today?",
  "contentType": "TEXT",
  "messageType": "CHAT",
  "color": "#3B82F6",
  "replyToId": null,
  "fileUrl": null,
  "fileName": null,
  "fileSize": null,
  "timestamp": "2026-09-18T15:30:00.120Z",
  "status": "SENT",
  "isEdited": false,
  "isDeleted": false,
  "isReacted": false,
  "reactions": null
}
```

*Delivery Semantics*:
- When User A sends a message, User B receives this payload to display the message.
- User A also receives this payload via their personal queue containing the matching `localId` and assigned MongoDB `id`, serving as an instant delivery acknowledgment (ACK).

---

### 4.2. User Notifications (Friend Requests & Read Receipts)
- **Client Subscription**: `/user/queue/notifications`
- **Payload Model (`NotificationResponse<T>`)**:
  - **Friend Request Notification**: Dispatched when an invitation is received or accepted.
  - **Read Receipt Notification (`ReadReceiptResponse`)**:
    ```json
    {
      "type": "READ_RECEIPT",
      "data": {
        "conversationId": "alice_bob",
        "reader": "bob_smith",
        "sender": "alice_smith",
        "readTimestamp": "2026-09-18T15:32:10.500Z"
      }
    }
    ```

---

### 4.3. Global Public Announcements
- **Client Subscription**: `/topic/public`
- **Payload Model (`MessageSystemResponse`)**:

```json
{
  "id": "65e52c901f938b29c8e1a789",
  "sender": "admin_system",
  "content": "Scheduled server maintenance will take place tonight at 02:00 UTC.",
  "timestamp": "2026-09-18T15:00:00.000Z"
}
```

---

### 4.4. Targeted WebSocket Error Alerts
- **Client Subscription**: `/user/queue/errors`
- Delivers real-time business and validation errors specifically directed to the offending client session.

---

## 5. Standardized Error Handling (`ErrorSocketResponse`)

When an exception occurs during frame parsing, validation, or business execution, `StompSubProtocolErrorHandler` generates a standardized JSON payload:

```json
{
  "code": 400,
  "errorCode": "STOMP_ERROR",
  "message": "Users must have an accepted friendship before exchanging private messages.",
  "request": null
}
```

Client-side STOMP listeners intercept this frame or `/user/queue/errors` messages to present contextual UI notifications (e.g., error toast dialogs) without terminating the underlying WebSocket transport connection.
