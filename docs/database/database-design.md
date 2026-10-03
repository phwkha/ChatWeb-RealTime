# Polyglot Database Design

This document details the data storage architecture of **ChatWeb** across its three dedicated persistence engines: **PostgreSQL (Relational)**, **MongoDB (Document Store)**, and **Redis Stack (In-Memory Data Structures & Filters)**.

---

## 1. Relational Tier: PostgreSQL (RDBMS)

PostgreSQL serves as the authoritative source of truth for user authentication, role-based access control (RBAC), friendship graphs, address records, persistent in-app notifications, and moderation reports.

### 1.1. Entity-Relationship Diagram (ERD)

```mermaid
erDiagram
    ROLES ||--o{ USERS : "assigned_to (role_id)"
    ROLES }o--o{ PERMISSIONS : "includes (role_has_permission)"
    USERS ||--o{ FRIENDSHIPS : "requests (requester_id)"
    USERS ||--o{ FRIENDSHIPS : "receives (addressee_id)"
    USERS ||--o{ ADDRESSES : "owns (user_id)"
    USERS ||--o{ NOTIFICATIONS : "receives (recipient_id)"
    USERS ||--o{ NOTIFICATIONS : "triggers (sender_id)"
    USERS ||--o{ REPORTS : "files (reporter_id)"
    USERS ||--o{ REPORTS : "reported_in (reported_user_id)"
    USERS ||--o{ REPORTS : "resolves (resolved_by_id)"

    USERS {
        bigint id PK
        varchar auth_provider "LOCAL, GOOGLE, FACEBOOK, GITHUB"
        varchar provider_id UK "Nullable"
        varchar username UK "Not Null, Unique"
        varchar password "BCrypt Hash, Not Null"
        varchar email UK "Unique"
        varchar phone "Nullable"
        boolean is_online "Default false, Indexed"
        varchar user_status "ACTIVE, INACTIVE, LOCKED, UNVERIFIED"
        bigint role_id FK "Not Null"
        varchar first_name "Nullable"
        varchar last_name "Nullable"
        varchar avatar "Cloudinary URL"
        date birthday "Nullable"
        varchar gender "MAN, WOMAN"
        varchar language "Default 'vi'"
        integer token_version "Default 0 - Single Sign-Out"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    ROLES {
        bigint id PK
        varchar name UK "Not Null, Unique (e.g. ADMIN, USER)"
        varchar description "Human-readable description"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    PERMISSIONS {
        bigint id PK
        varchar name UK "Not Null, Unique (e.g. ADMIN_VIEW_USERS)"
        varchar description "Permission scope"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    ROLE_HAS_PERMISSION {
        bigint role_id PK,FK "Not Null"
        bigint permission_id PK,FK "Not Null"
    }

    FRIENDSHIPS {
        bigint id PK
        bigint requester_id FK "Not Null"
        bigint addressee_id FK "Not Null"
        varchar status "PENDING, ACCEPTED, BLOCKED"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    ADDRESSES {
        bigint id PK
        bigint user_id FK "Nullable, ON DELETE CASCADE"
        varchar house_number "Nullable"
        varchar street "Nullable"
        varchar ward "Nullable"
        varchar district "Nullable"
        varchar city "Not Null"
        varchar country "Not Null"
        varchar postal_code "Nullable"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    NOTIFICATIONS {
        bigint id PK
        bigint recipient_id FK "Not Null"
        bigint sender_id FK "Nullable, ON DELETE CASCADE"
        varchar type "Not Null (13 types: REQUEST_SENT_SUCCESS, YOU_ACCEPTED, FRIEND_REQUEST, FRIEND_ACCEPTED, UNFRIENDED, REQUEST_CANCELLED, REQUEST_REJECTED, USER_ONLINE, USER_OFFLINE, EDIT_MESSAGE, REVOKE_MESSAGE, REACT_MESSAGE, STATUS_MESSAGE)"
        varchar target_type "Nullable (USER, MESSAGE, CONVERSATION, SYSTEM)"
        varchar target_id "Nullable"
        boolean is_read "Default false, Indexed"
        text content "Not Null"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }

    REPORTS {
        bigint id PK
        bigint reporter_id FK "Not Null"
        bigint reported_user_id FK "Not Null"
        varchar reason "Not Null (SPAM, HARASSMENT, INAPPROPRIATE, IMPERSONATION, OTHER)"
        text details "Nullable"
        varchar status "Not Null (PENDING, RESOLVED, DISMISSED)"
        text resolution_note "Nullable"
        bigint resolved_by_id FK "Nullable"
        timestamp resolve_at "Nullable"
        timestamp create_at "Creation timestamp"
        timestamp update_at "Auto-updated timestamp"
        bigint version "Default 0 - Optimistic Locking"
    }
```

### 1.2. Index Optimization Strategy

- **Table `users`**:
  - `idx_user_status` on `(user_status)`: Accelerates queries filtering active, locked, or unverified accounts.
  - `idx_user_role_id` on `(role_id)`: Speeds up authority loading during JWT authentication filter execution.
  - `idx_user_is_online` on `(is_online)`: Speeds up bulk online status resets (`WHERE is_online = true`) during system restart.
  - Unique B-Tree Constraints/Indexes: `users_username_key`, `users_email_key`, `users_provider_id_key`.
- **Table `friendships`**:
  - Unique Constraint `uq_friendship_requester_addressee` on `(requester_id, addressee_id)`: Prevents duplicate friendship requests.
  - Composite Index `idx_friendship_requester_status` on `(requester_id, status)`: Accelerates outgoing friend request lookups.
  - Composite Index `idx_friendship_addressee_status` on `(addressee_id, status)`: Accelerates incoming pending invitation lookups.
- **Table `addresses`**:
  - `idx_address_user_id` on `(user_id)`: Optimizes user address book queries.
- **Table `roles` & `permissions`**:
  - Unique Indexes on `name`: Guarantees deterministic role and permission lookup (`ADMIN`, `USER`, `ADMIN_VIEW_REPORTS`, etc.).
- **Table `notifications`**:
  - `idx_notifications_recipient_create_at_id` on `(recipient_id, create_at DESC, id DESC)`: High-performance cursor pagination for in-app notification drawers.
  - Partial Index `idx_notifications_recipient_unread` on `(recipient_id) WHERE is_read = false`: Ultra-fast query for unread notification count badge.
  - `idx_notifications_sender_id` on `(sender_id)`: Accelerates foreign key cascade cleanup and duplicate detection queries.
- **Table `reports`**:
  - `idx_reports_status_create_at` on `(status, create_at DESC)`: Speeds up administrative triage and dashboard queues for pending moderation reports.
  - `idx_reports_reported_user` on `(reported_user_id)`: Accelerates moderation audits against reported accounts.
  - `idx_reports_reporter_id` on `(reporter_id)`: Accelerates queries by reporting users (`GET /api/reports/me`).
  - `idx_reports_reporter_reported_status` on `(reporter_id, reported_user_id, status)`: Enforces idempotency preventing multiple active pending reports between the same users.

---

## 2. Document Tier: MongoDB (NoSQL)

MongoDB stores unstructured, high-frequency chat messages, read receipts, and transient administrative broadcasts.

### 2.1. Collection `messages`

Stores one-on-one direct messages, attachments, and reaction metadata.

| Field Name | BSON Type | Constraints & Description |
| :--- | :--- | :--- |
| `_id` | `ObjectId / String` | Unique message identifier. |
| `conversationId` | `String` | Normalized deterministic ID: `{minUsername}_{maxUsername}` (e.g. `alice_bob`). |
| `sender` | `String` | Sender username (Indexed). |
| `recipient` | `String` | Recipient username. |
| `content` | `String` | Message text content (null for pure media). |
| `contentType` | `String` (Enum) | `TEXT`, `IMAGE`, `VIDEO`, `FILE`. |
| `messageType` | `String` (Enum) | `CHAT`, `TYPING`. |
| `color` | `String` | Hex color code for customized chat bubble UI. |
| `replyToId` | `String` | `_id` of the quoted parent message (null if direct message). |
| `fileUrl` | `String` | Cloudinary CDN URL for media attachments. |
| `fileName` | `String` | Original filename of uploaded media. |
| `fileSize` | `Long` | File size in bytes. |
| `timestamp` | `Date / ISODate` | Message creation timestamp. |
| `status` | `String` (Enum) | `SENDING`, `SENT`, `READ`. |
| `isEdited` | `Boolean` | Flag indicating whether the message content has been edited. |
| `isDeleted` | `Boolean` | Soft-deletion flag (revoked message). |
| `isReacted` | `Boolean` | Flag indicating whether emoji reactions exist. |
| `reactions` | `Map<String, String>` | Key-value dictionary of reactions: `{ "username": "emoji" }`. |

#### Compound Indexes:
1. **`conv_msg_time_idx`**: `{ conversationId: 1, messageType: 1, timestamp: -1 }`  
   *Purpose*: High-speed cursor-based pagination of chat history in reverse chronological order.
2. **`unread_msg_idx`**: `{ recipient: 1, messageType: 1, isDeleted: 1, sender: 1 }`  
   *Purpose*: Fast aggregation and counting of unread messages grouped by sender.
3. **`conv_content_time_idx`**: `{ conversationId: 1, messageType: 1, isDeleted: 1, timestamp: -1 }`  
   *Purpose*: Keyword searching within active (non-deleted) conversation messages.
4. **`sender_idx`**: `{ sender: 1 }`  
   *Purpose*: Accelerates user activity and message existence queries.

---

### 2.2. Collection `read_receipts`

Implements monotonic watermark tracking for user read positions.

| Field Name | BSON Type | Constraints & Description |
| :--- | :--- | :--- |
| `_id` | `String` | Unique receipt identifier: `{conversationId}:{username}`. |
| `conversationId` | `String` | Canonical conversation identifier (Indexed). |
| `username` | `String` | Reader username (Indexed). |
| `lastReadTimestamp` | `Date / ISODate` | Monotonically updated read watermark timestamp. |
| `lastReadMessageId` | `String` | Identifier of the last message consumed by reader. |

#### Indexes:
- **`conv_user_idx`**: `{ conversationId: 1, username: 1 }`, `unique: true`  
  Guarantees exactly one watermark record per user per conversation.
- **`conversationId_idx`**: `{ conversationId: 1 }`
- **`username_idx`**: `{ username: 1 }`

---

### 2.3. Collection `system_message`

Stores administrative broadcast announcements with automated lifecycle expiration.

| Field Name | BSON Type | Constraints & Description |
| :--- | :--- | :--- |
| `_id` | `String` | Unique announcement identifier. |
| `sender` | `String` | Admin username who published the announcement. |
| `content` | `String` | Broadcast message text. |
| `timestamp` | `Date / ISODate` | Publication timestamp (Indexed descending). |
| `expiresAt` | `Date / ISODate` | Timestamp after which the announcement becomes obsolete (TTL Indexed). |

#### Indexes:
- **`expiresAt_ttl_idx`**: `{ expiresAt: 1 }`, `expireAfterSeconds: 0`  
  MongoDB background TTL thread automatically purges expired announcements without application intervention.
- **`system_message_timestamp_idx`**: `{ timestamp: -1 }`  
  High-speed cursor pagination for administrative announcements.

---

### 2.4. Automated MongoDB Initialization Script (`init-mongo.js`)

ChatWeb includes an automated JavaScript database initialization file mounted to `/docker-entrypoint-initdb.d/init-mongo.js:ro` in `docker-compose.yml`:

```javascript
const dbName = (typeof process !== 'undefined' && process.env && (process.env.MONGO_INITDB_DATABASE || process.env.MONGO_DB))
    ? (process.env.MONGO_INITDB_DATABASE || process.env.MONGO_DB)
    : 'chatweb';
const targetDb = db.getSiblingDB(dbName);

// 1. messages collection & indexes
targetDb.createCollection('messages');
targetDb.messages.createIndex({ conversationId: 1, messageType: 1, timestamp: -1 }, { name: 'conv_msg_time_idx' });
targetDb.messages.createIndex({ recipient: 1, messageType: 1, isDeleted: 1, sender: 1 }, { name: 'unread_msg_idx' });
targetDb.messages.createIndex({ conversationId: 1, messageType: 1, isDeleted: 1, timestamp: -1 }, { name: 'conv_content_time_idx' });
targetDb.messages.createIndex({ sender: 1 }, { name: 'sender_idx' });

// 2. read_receipts collection & indexes
targetDb.createCollection('read_receipts');
targetDb.read_receipts.createIndex({ conversationId: 1, username: 1 }, { name: 'conv_user_idx', unique: true });
targetDb.read_receipts.createIndex({ conversationId: 1 }, { name: 'conversationId_idx' });
targetDb.read_receipts.createIndex({ username: 1 }, { name: 'username_idx' });

// 3. system_message collection & TTL / pagination indexes
targetDb.createCollection('system_message');
targetDb.system_message.createIndex({ expiresAt: 1 }, { name: 'expiresAt_ttl_idx', expireAfterSeconds: 0 });
targetDb.system_message.createIndex({ timestamp: -1 }, { name: 'system_message_timestamp_idx' });
```

---

## 3. In-Memory Tier: Redis Stack

Redis Stack serves as the ultra-fast distributed coordinating fabric across all backend instances.

### 3.1. Master Redis Key Catalog

| Key Pattern | Data Structure | TTL | Purpose & Usage |
| :--- | :--- | :--- | :--- |
| `online_users` | **Sorted Set (ZSet)** | Persistent | Holds usernames of currently online users. `score` = Epoch timestamp of latest ping/connect. |
| `online_users_count` | **Hash** | Persistent | Field: `username`, Value: integer count of active WebSocket tabs/sessions. |
| `presence:offline_queue` | **Sorted Set (ZSet)** | Transient | Distributed debounce queue. `score` = Epoch timestamp deadline (`now + 5000ms`). Polled by `SessionCleanupScheduler`. |
| `ws:routing:servers:{username}` | **Hash** | Persistent | Field: `serverId`, Value: active session count on that specific backend instance. |
| `channel:server:{serverId}` | **Pub/Sub Channel** | N/A (Stream) | Dedicated channel for cross-server WebSocket frame forwarding. |
| `ws:dedup:{sender}:{localId}` | **String** | **300 seconds** | Set via `SETNX`. Prevents duplicate STOMP message execution during network retries. |
| `chat:recent:hash:{convId}` | **Hash** | **1 hour (3600s ±300s jitter)** | Caches full metadata of recent messages (capped at 50 messages per conversation) for fast conversation list rendering. |
| `chat:recent:zset:{convId}` | **Sorted Set (ZSet)** | **1 hour (3600s ±300s jitter)** | Stores recent message IDs with `score` = timestamp (capped at 50 messages) for fast ID range slicing. |
| `read_receipt:{convId}:{username}`| **String** | **7 days** | Caches latest read watermark timestamp for fast frontend synchronization. |
| `unread_counts:{username}` | **Hash** | **7 days fallback TTL + jitter** | Caches unread message counts per conversation partner. Evicted upon read receipt upsert. |
| `notif:unread:{username}` | **String** | Evicted on change| Caches unread notification count badge for the user. |
| `relation:{user1}:{user2}` | **String** | **1 hour (NONE), 1 day (PENDING), 7 days (ACCEPTED/BLOCKED)** | Caches friendship status (`ACCEPTED`, `PENDING:<requester>`, `BLOCKED:<blocker>`, `NONE`) with alphabetically sorted usernames. |
| `blacklist:{token}` | **String** | Remaining JWT TTL | Set upon logout to immediately invalidate unexpired JWT Access Tokens. |
| `rt:{token}` | **Serialized Object** | **7 days** | Stores `RefreshTokenData` (UUID, username, `tokenVersion`) for token rotation and validation. |
| `rt_grace:{token}` | **String / Object** | **15 seconds** | Short grace period window caching the new token pair during silent refresh to allow in-flight concurrent requests. |
| `register:{email}` | **Serialized Object** | **5 minutes** | Temporary registration cache storing pending user details and verification OTP. |
| `otp:{OtpType}:{identifier}` | **String** | **5 minutes** | Stores 6-digit OTP code (`OtpType`: `EMAIL_CHANGE`, `PASSWORD_RESET`, `PHONE_CHANGE`, `DEVICE_VERIFICATION`). |
| `otp:{OtpType}:{identifier}:attempts` | **String (Counter)** | **5 minutes** | Tracks OTP verification attempts (max 5) before invalidating the code. |
| `cooldown:resend:{identifier}` | **String** | **60 seconds** | Cooldown throttle preventing rapid repeated requests for OTP resends. |
| `lock:session_cleanup` | **String (Distributed Lock)** | **20 seconds** | Distributed lock acquiring exclusive execution rights for `SessionCleanupScheduler`. |
| `lock:presence_debounce` | **String (Distributed Lock)** | **2 seconds** | Distributed lock preventing duplicate processing of the offline debounce queue. |
| `lock:notification_cleanup` | **String (Distributed Lock)** | **60 seconds** | Distributed lock ensuring only one node runs `NotificationCleanupScheduler` (purges expired notifications). |
| `user_details::{username}` | **Serialized Object** | **Cache Default TTL** | Spring `@Cacheable` storing hydrated `UserDetails` to bypass PostgreSQL on repeated authenticated requests. |
| `filter:usernames` | **Cuckoo Filter** | Persistent | High-speed probabilistic filter checking if a username is already registered (`CF.EXISTS`). |
| `filter:emails` | **Cuckoo Filter** | Persistent | High-speed probabilistic filter checking if an email address is registered (`CF.EXISTS`). |
| `rate_limit:{key}` | **Sorted Set (ZSet)** | Window + 2s | Sliding Window Log rate limiter managed atomically via custom Lua scripts. |
| `idempotent:{key}:{idempotencyKey}`| **String** | **300–600 seconds**| Distributed lock preventing duplicate execution of sensitive REST endpoints (e.g. file upload). |
