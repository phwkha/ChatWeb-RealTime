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
- **Custom Authentication Error Classification (`4011` / `4012`)**:
  - **`4011` (`TOKEN_EXPIRED`)**: Dispatched when the JWT Access Token has expired (`ExpiredJwtException`).
  - **`4012` (`TOKEN_INVALID`)**: Dispatched when the token signature is invalid, malformed, blacklisted, or has an outdated `token_version`.
  - **Client handling (`useChatSocket.js`)**: Both codes are treated as auth errors. The client pauses reconnects, attempts a silent token refresh via `/api/auth/refresh-token` (server-side 15-second grace window `rt_grace:<token>` tolerates concurrent refreshes), updates STOMP `connectHeaders`, and reactivates the connection. If the refresh fails, `apiClient.js` fires the global session-expired event (`chatweb:session-expired`) and the user must sign in again.
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
  - `TYPING`: Transient typing indicator event (bypasses Kafka and MongoDB persistence).
- `color` (*String, Optional*): UI bubble accent color hex code.
- `replyToId` (*String, Optional*): MongoDB `_id` of a quoted parent message.
- `fileUrl` (*String, Optional*): Direct Cloudinary CDN URL for media payloads.
- `fileName` / `fileSize` (*Optional*): Metadata for attachments.

#### Typing Indicator Protocol:
When a user begins or stops typing, the client dispatches a lightweight frame with `messageType: TYPING` and JSON formatted `content`:
```json
{
  "recipient": "bob_smith",
  "content": "__CHATWEB_TYPING__:{\"active\":true}",
  "messageType": "TYPING"
}
```
Setting `active: false` signals that typing has ceased. The backend routes this transient payload directly to the recipient's session without publishing to Kafka or persisting to MongoDB.

#### Inbound STOMP Rate Limiting:
Protected by atomic Redis sliding window rate limiting in `ChatServiceImpl`:
- **`ws_chat_send`**: 30 requests/minute per authenticated user for `CHAT` messages.
- **`ws_chat_typing`**: 60 requests/minute per authenticated user for `TYPING` events.
Violations return an `ErrorSocketResponse` to `/user/queue/errors` with code `429` / `RATE_LIMITED`.

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

### 4.2. User Notifications (Complete 13-Type Catalog)
- **Client Subscription**: `/user/queue/notifications`
- **Payload Model (`SocketNotificationResponse<T>`)**:

```json
{
  "id": 105,
  "notificationId": 105,
  "type": "FRIEND_REQUEST",
  "relatedUsername": "charlie_brown",
  "message": "charlie_brown sent you a friend request",
  "data": null
}
```

The system dispatches 13 distinct notification types (`NotificationsType.java`) across friendship, presence, message mutations, and read receipts:

| Notification Type | Target Destination | Description & Payload Contents |
| :--- | :--- | :--- |
| `REQUEST_SENT_SUCCESS` | Requester queue | Confirmation dispatched to sender upon successfully creating an outgoing friend invitation. |
| `YOU_ACCEPTED` | Acceptor queue | Confirmation dispatched to the user who accepted a friend request. |
| `FRIEND_REQUEST` | Addressee queue | Real-time alert dispatched to the recipient of a new friend invitation (`data: FriendResponse`). |
| `FRIEND_ACCEPTED` | Requester queue | Dispatched to original requester when their friend invitation is accepted. |
| `UNFRIENDED` | Ex-friend queue | Alert dispatched to the other party when a friendship is terminated. |
| `REQUEST_CANCELLED` | Addressee queue | Alert dispatched when a pending friend invitation is retracted by the requester. |
| `REQUEST_REJECTED` | Requester queue | Alert dispatched when a friend invitation is declined by the addressee. |
| `USER_ONLINE` | Mutual friends | Dispatched immediately upon WebSocket connection handshake to mutual accepted friends (`relatedUsername: "alice"`, `data: null`). |
| `USER_OFFLINE` | Mutual friends | Dispatched after the 5-second debounce window expires in `SessionCleanupScheduler` when all user sessions disconnect (`data: null`). |
| `EDIT_MESSAGE` | Chat recipient | Dispatched when a message is edited, carrying the updated `ChatMessageResponse` in `data`. |
| `REVOKE_MESSAGE` | Chat recipient | Dispatched when a message is revoked, carrying the updated `ChatMessageResponse` (`deleted: true`) in `data`. |
| `REACT_MESSAGE` | Chat recipient | Dispatched when an emoji reaction is added/updated, carrying the updated `ChatMessageResponse` (with `reactions` map) in `data`. |
| `STATUS_MESSAGE` | Original sender | Dispatched upon atomic read receipt watermark upsert. Carries `data: { conversationId, reader, sender, readTimestamp }`. |

#### Sample Payloads:

1. **Read Receipt Watermark (`STATUS_MESSAGE`)**:
```json
{
  "id": null,
  "notificationId": null,
  "type": "STATUS_MESSAGE",
  "relatedUsername": "bob_smith",
  "message": "Message status updated",
  "data": {
    "conversationId": "alice_bob",
    "reader": "bob_smith",
    "sender": "alice_smith",
    "readTimestamp": "2026-09-18T15:32:10.500Z"
  }
}
```

2. **Presence Status (`USER_ONLINE` / `USER_OFFLINE`)**:
```json
{
  "id": null,
  "notificationId": null,
  "type": "USER_ONLINE",
  "relatedUsername": "bob_smith",
  "message": "User is online",
  "data": null
}
```

3. **Message Mutation (`EDIT_MESSAGE` / `REVOKE_MESSAGE` / `REACT_MESSAGE`)**:
```json
{
  "id": 231,
  "notificationId": 231,
  "type": "REACT_MESSAGE",
  "relatedUsername": "bob_smith",
  "message": "has reacted to a message",
  "data": {
    "id": "65e52b121f938b29c8e1a456",
    "conversationId": "alice_bob",
    "sender": "alice_smith",
    "recipient": "bob_smith",
    "content": "See you tomorrow!",
    "contentType": "TEXT",
    "messageType": "CHAT",
    "timestamp": "2026-09-18T15:30:00.000Z",
    "edited": false,
    "deleted": false,
    "reacted": true,
    "reactions": { "bob_smith": "HEART" }
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

### Standard WebSocket Error Codes:

| Code | Error Code | Trigger Condition & Client Action |
| :--- | :--- | :--- |
| `400` | `STOMP_ERROR` | Validation error, malformed JSON, or business violation (e.g. messaging non-friend). Handled via UI toast notification. |
| `429` | `RATE_LIMITED` | Inbound STOMP rate limit exceeded (`ws_chat_send` > 30/min or `ws_chat_typing` > 60/min). Handled by delaying retries. |
| `4011` | `TOKEN_EXPIRED` | Expired JWT token in STOMP session. Client (`useChatSocket.js`) attempts a silent refresh via `/api/auth/refresh-token` and reconnects; on refresh failure the session-expired flow is triggered. |
| `4012` | `TOKEN_INVALID` | Invalid token signature, blacklisted token, or `token_version` mismatch. Client handling is identical to `4011` (refresh attempt, then session-expired on failure). |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected backend runtime exception. |
