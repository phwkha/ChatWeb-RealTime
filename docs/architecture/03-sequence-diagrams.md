# Core Business Sequence Diagrams

This document details the end-to-end communication flows across the Client, Nginx Ingress, Spring Boot Backend, Redis Stack, Apache Kafka, PostgreSQL, and MongoDB for the five most critical business operations in ChatWeb.

---

## 1. Real-Time Chat Pipeline

Traces the complete lifecycle of a private message from transmission by Client A to instant screen rendering for Client B and asynchronous bulk persistence into MongoDB.

```mermaid
sequenceDiagram
    autonumber
    actor Sender as Client A (Sender)
    participant Nginx as Nginx Proxy
    participant WSInterceptor as ChannelInterceptor
    participant Controller as ChatController
    participant Service as ChatServiceImpl
    participant Redis as Redis Stack
    participant Kafka as Kafka (chat-messages)
    participant ChatConsumer as ChatConsumer (Fast-Push)
    participant WSRouting as WebSocketRoutingService
    participant SaveConsumer as DBWriteBehindConsumer
    participant Mongo as MongoDB (messages)
    actor Recipient as Client B (Recipient)

    Sender->>Nginx: SEND /app/chat/sendPrivateMessage (STOMP frame)
    Nginx->>WSInterceptor: Forward WebSocket frame
    WSInterceptor->>WSInterceptor: Validate JWT, Check Redis Blacklist & Token Version
    WSInterceptor->>Controller: Forward authenticated message
    Controller->>Service: sendPrivateMessage(sender, request)
    
    rect rgb(240, 248, 255)
        note over Service, Redis: Phase 1: Deduplication & Validation
        Service->>Redis: SETNX ws:dedup:{sender}:{localId} (TTL=300s)
        alt Key Already Exists (Client retry caused by network lag)
            Redis-->>Service: Return FALSE
            Service-->>Sender: Silently acknowledge / suppress duplicate
        else Key Created Successfully
            Redis-->>Service: Return TRUE
        end
        Service->>Service: Verify friendship & recipient existence
    end

    rect rgb(255, 250, 240)
        note over Service, Redis: Phase 2: Cache Most Recent Conversation Snapshot
        Service->>Redis: Update chat:recent:hash:{convId} & chat:recent:zset:{convId}
    end

    rect rgb(240, 255, 240)
        note over Service, Kafka: Phase 3: Binary Event Publication
        Service->>Kafka: Publish ChatMessageAvro to topic "chat-messages"
    end

    par Branch 1: Fast-Push Stream (<10ms latency)
        Kafka->>ChatConsumer: Consume ChatMessageAvro payload
        ChatConsumer->>WSRouting: routeMessage(recipient, /queue/messages)
        WSRouting->>Redis: Lookup target server node at ws:routing:servers:{recipient}
        alt Recipient is on Current Node
            WSRouting->>Recipient: Push STOMP frame directly to local session
        else Recipient is on Different Node
            WSRouting->>Redis: Publish to channel:server:{targetServerId}
            Redis->>Recipient: Target node receives Pub/Sub & delivers to Client B
        end
        ChatConsumer->>WSRouting: routeMessage(sender, /queue/messages) [Delivery ACK]
        WSRouting->>Sender: Receive ACK with localId to update UI message status
    and Branch 2: Write-Behind Stream (Batch Persistence)
        Kafka->>SaveConsumer: Poll batch of up to 200 records
        SaveConsumer->>Mongo: Bulk unordered insert into "messages" collection
        alt DuplicateKeyException Encountered
            Mongo-->>SaveConsumer: Safely ignored (Idempotent write)
        else Unrecoverable Storage Exception
            SaveConsumer->>Kafka: Publish failed records to "chat-messages-save-dlt"
        end
    end
```

---

## 2. Authentication, Token Rotation, and Single Sign-Out

Illustrates user login, access token issuance in headers, refresh token storage in HttpOnly cookies, token rotation, and instant global revocation via `token_version`.

```mermaid
sequenceDiagram
    autonumber
    actor User as User Client
    participant Nginx as Nginx Ingress
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthenticationServiceImpl
    participant Cuckoo as CuckooFilterService
    participant DB as PostgreSQL
    participant Redis as Redis Stack
    participant JWT as JwtService

    User->>Nginx: POST /api/auth/login { username, password }
    Nginx->>AuthCtrl: Forward request
    AuthCtrl->>AuthSvc: login(loginRequest)
    
    rect rgb(240, 248, 255)
        note over AuthSvc, Cuckoo: Sub-millisecond Preflight Existence Check
        AuthSvc->>Cuckoo: exists("filter:usernames", username)
        alt Not Found in Cuckoo Filter (Zero False Negatives)
            Cuckoo-->>AuthSvc: False (Account definitely does not exist)
            AuthSvc-->>User: 404 / 401 Rejection without querying PostgreSQL
        else Might Exist in System
            Cuckoo-->>AuthSvc: True
            AuthSvc->>DB: Query UserEntity by username
        end
    end

    AuthSvc->>AuthSvc: Verify password against BCrypt hash
    AuthSvc->>JWT: Generate Access Token (claim v = user.token_version, exp = 15m)
    AuthSvc->>JWT: Generate Opaque Refresh Token (UUID)
    JWT->>Redis: Save RefreshTokenData to rt:{uuid} (TTL = 7 days)
    
    AuthSvc-->>AuthCtrl: Return LoginResponse (Tokens + UserDTO)
    AuthCtrl-->>User: Response 200 OK<br/>Header: Authorization: Bearer <accessToken><br/>Set-Cookie: refreshToken=<UUID>#59; Path=/api/auth#59; HttpOnly#59; SameSite=Strict

    note over User, DB: Token Rotation Flow
    User->>AuthCtrl: POST /api/auth/refresh-token (Cookie: refreshToken)
    AuthCtrl->>AuthSvc: refreshToken(refreshToken)
    AuthSvc->>Redis: GET rt:{token} (Fetch RefreshTokenData)
    AuthSvc->>DB: Verify tokenData.tokenVersion == user.tokenVersion
    AuthSvc->>Redis: DEL rt:{token} (Revoke old refresh token immediately)
    AuthSvc->>JWT: Issue new Access Token + new Opaque Refresh Token
    JWT->>Redis: SET rt:{newToken} -> RefreshTokenData (TTL 7 days)
    AuthCtrl-->>User: Return new tokens (Authorization Header + new Cookie)

    note over User, DB: Single Sign-Out Across All Devices (O(1) Revocation)
    User->>AuthCtrl: POST /api/auth/logout-all-devices
    AuthCtrl->>AuthSvc: logoutAllDevices(username)
    AuthSvc->>DB: UPDATE users SET token_version = token_version + 1 WHERE username = ?
    AuthSvc->>Redis: Evict cached user details & Blacklist current Access Token
    AuthCtrl-->>User: 200 OK (All tokens with old token_version are instantly rejected)
```

---

## 3. Distributed Presence Lifecycle & 5-Second Debounce Queue

Demonstrates connection tracking and the elimination of presence flapping when users refresh pages or experience transient disconnections.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser Client
    participant WS as WebSocketListener
    participant Redis as Redis Stack
    participant DB as PostgreSQL
    participant Scheduler as SessionCleanupScheduler
    actor Friends as Connected Friends

    note over Client, Redis: Phase 1: Connection Established (CONNECT)
    Client->>WS: STOMP CONNECT frame established
    WS->>Redis: ZREM presence:offline_queue {username} (Cancel any pending offline tasks)
    WS->>Redis: HINCRBY online_users_count {username} +1
    WS->>Redis: ZADD online_users {score = currentTimestamp} {username}
    alt Initial Connection (count == 1)
        WS->>DB: UPDATE users SET is_online = true WHERE username = ?
        WS->>Friends: Broadcast User Online notification
    else Additional Tab Opened (count > 1)
        WS->>WS: Maintain online state (Suppress redundant broadcast)
    end

    note over Client, Redis: Phase 2: Connection Dropped (Tab Closed or Page Reload)
    Client->>WS: STOMP DISCONNECT event
    WS->>Redis: HINCRBY online_users_count {username} -1
    alt No Remaining Sessions (count <= 0)
        WS->>Redis: ZADD presence:offline_queue {score = now + 5000ms} {username}
        note over WS, Redis: User queued in distributed debounce with 5-second deadline
    end

    note over Client, Redis: Phase 3a: Reconnection Within 5 Seconds (Page Refresh Complete)
    Client->>WS: STOMP CONNECT from new page
    WS->>Redis: ZREM presence:offline_queue {username}
    WS->>Redis: HINCRBY online_users_count {username} +1
    note over WS, Friends: Pending offline transition canceled! Friends see zero presence flapping.

    note over Scheduler, Friends: Phase 3b: 5-Second Timeout Expired (User Genuinely Offline)
    Scheduler->>Redis: ZRANGEBYSCORE presence:offline_queue 0 {now}
    Scheduler->>Redis: HGET online_users_count {username}
    alt User Session Count Still <= 0
        Scheduler->>DB: UPDATE users SET is_online = false WHERE username = ?
        Scheduler->>Redis: ZREM online_users {username}
        Scheduler->>Friends: Broadcast User Offline notification
    end
    Scheduler->>Redis: ZREM presence:offline_queue {username}
```

---

## 4. Friend Request & Real-Time Notification Pipeline

Illustrates sending and accepting friend requests with idempotency protection and push notification routing.

```mermaid
sequenceDiagram
    autonumber
    actor Alice as Alice (Requester)
    participant FriendCtrl as FriendController
    participant FriendSvc as FriendServiceImpl
    participant Redis as Redis Stack
    participant DB as PostgreSQL
    participant Kafka as Kafka (friend-notifications)
    participant FriendConsumer as FriendConsumer
    participant WSRouting as WebSocketRoutingService
    actor Bob as Bob (Addressee)

    Alice->>FriendCtrl: POST /api/friends/request { targetUsername: "bob" }
    FriendCtrl->>FriendSvc: sendFriendRequest("alice", "bob")
    FriendSvc->>DB: Check neither user is blocked & relationship doesn't exist
    FriendSvc->>DB: INSERT INTO friendships (requester_id, addressee_id, status='PENDING')
    FriendSvc->>Kafka: Publish FriendNotificationPayload to "friend-notifications"
    FriendSvc-->>Alice: 200 OK (Request sent)

    Kafka->>FriendConsumer: Consume notification event
    FriendConsumer->>WSRouting: routeMessage("bob", "/queue/notifications", payload)
    WSRouting->>Bob: Deliver STOMP frame to /user/queue/notifications (Unread badge increments)

    note over Bob, Alice: Bob Accepts Friend Request with Idempotency Key
    Bob->>FriendCtrl: POST /api/friends/accept { targetUsername: "alice" }<br/>Header: X-Idempotency-Key: <UUID>
    FriendCtrl->>Redis: SETNX idempotent:friend_accept:<UUID> (TTL 300s)
    FriendCtrl->>FriendSvc: acceptFriendRequest("bob", "alice")
    FriendSvc->>DB: UPDATE friendships SET status = 'ACCEPTED' WHERE ...
    FriendSvc->>Kafka: Publish ACCEPTED event to "friend-notifications"
    FriendCtrl-->>Bob: 200 OK (Friendship established)

    Kafka->>FriendConsumer: Consume notification event
    FriendConsumer->>WSRouting: routeMessage("alice", "/queue/notifications", payload)
    WSRouting->>Alice: Deliver STOMP frame: "Bob accepted your friend request!"
```

---

## 5. Watermark-Based Read Receipt Lifecycle

Illustrates atomic read receipt tracking, Redis TTL caching, and real-time read receipt updates sent back to the message author.

```mermaid
sequenceDiagram
    autonumber
    actor Bob as Bob (Reader)
    participant MsgCtrl as MessageController
    participant MsgSvc as MessageServiceImpl
    participant Mongo as MongoDB (read_receipts)
    participant Redis as Redis Stack
    participant Kafka as Kafka (message-update)
    participant UpdateConsumer as UpdateMessageConsumer
    participant WSRouting as WebSocketRoutingService
    actor Alice as Alice (Original Sender)

    Bob->>MsgCtrl: POST /api/messages/mark-as-read { sender: "alice", conversationId: "alice_bob" }
    MsgCtrl->>MsgSvc: markMessagesAsRead("bob", request)
    MsgSvc->>MsgSvc: Validate friendship & generate receipt ID: "alice_bob:bob"
    
    rect rgb(240, 248, 255)
        note over MsgSvc, Mongo: Atomic Monotonic Watermark Upsert ($max)
        MsgSvc->>Mongo: upsert({ _id: "alice_bob:bob" }, { $max: { lastReadTimestamp: now } })
    end

    rect rgb(255, 250, 240)
        note over MsgSvc, Redis: Cache Watermark & Invalidate Unread Counters
        MsgSvc->>Redis: SET read:receipt:alice_bob:bob = now (TTL = 7 days)
        MsgSvc->>Redis: DEL unread:counts:bob
    end

    rect rgb(240, 255, 240)
        note over MsgSvc, Kafka: Fanout Event over Kafka
        MsgSvc->>Kafka: Publish ReadReceiptResponse to topic "message-update"
    end
    MsgSvc-->>Bob: 200 OK

    Kafka->>UpdateConsumer: Consume update event (ReadReceiptResponse)
    UpdateConsumer->>WSRouting: routeMessage("alice", "/queue/notifications", receiptNotification)
    WSRouting->>Alice: Deliver STOMP frame to /user/queue/notifications
    note over Alice: Alice's client marks all messages up to "now" as READ with blue checkmarks
```
