# REST API Overview & Integration Guide

This document provides a comprehensive reference for the ChatWeb REST API, including global response envelope formats, error handling standards, idempotency controls, and an exhaustive catalog of all service modules.

---

## 1. Global Conventions

- **Base URLs**:
  - Local Ingress: `http://localhost`
  - Production / Staging: `https://<domain>`
- **Interactive Documentation**: OpenAPI / Swagger UI is available at:  
  👉 `http://localhost/swagger-ui/index.html`
- **Media Type**: `application/json; charset=UTF-8`
- **Internationalization (i18n)**: Response messages adapt to client preferences via the `Accept-Language` header (`en-US`, `vi-VN`, `ja-JP`).
- **Idempotency Safeguard**: For state-mutating requests (file uploads, friend requests and approvals), clients provide an `X-Idempotency-Key: <UUID>` header. The frontend `apiClient.js` automatically generates and attaches this header for all mutating HTTP methods (`POST`, `PUT`, `PATCH`, `DELETE`). The backend `IdempotentAspect.java` handles requests in a 2-phase lifecycle using Redis key `idempotent:{keyPrefix}:{username|anonymous}:{idempotencyKey}`:
  1. **Phase 1 (`PROCESSING`)**: Sets key with value `"PROCESSING"` via atomic `SETNX`. If a duplicate request arrives while still in `"PROCESSING"`, backend throws `DuplicateRequestException` (HTTP `409 Conflict`).
  2. **Phase 2 (`COMPLETED`)**: Upon successful execution, updates value to `"COMPLETED:" + serializedJson` with the configured TTL (300–600s). If the client resends the same key after completion, backend immediately replays the cached `200 OK` response payload. If an uncaught exception occurs during processing, the Redis key is evicted so that the operation can be retried safely.

### Standard Response Envelope (`ApiResponse<T>`)

Every REST response follows a structured envelope model (`ApiResponse.java`):

```json
{
  "code": 200,
  "status": "success",
  "message": "Operation completed successfully",
  "data": { ... }
}
```

#### Field Specifications:
- `code` (*Integer*): HTTP status code or application-specific error code (e.g. `200`, `201`, `400`, `4011`, `4012`, `429`).
- `status` (*String*): Execution status flag — either `"success"` or `"error"`.
- `message` (*String*): Human-readable localized description of the result or error message based on `Accept-Language`.
- `data` (*Generic `T`, Nullable*): Payload object for successful operations, or structured error metadata (`null` or field validation mappings).

#### Validation Error Handling (`MethodArgumentNotValidException`):
When input validation fails on `@Valid` request bodies, `GlobalExceptionHandler` traps the violation and populates `data` with a key-value mapping of invalid fields to their constraint violation messages:

```json
{
  "code": 400,
  "status": "error",
  "message": "Invalid input data",
  "data": {
    "email": "Email must be a well-formed email address",
    "password": "Password must be between 8 and 32 characters"
  }
}
```

#### Custom Authentication Error Codes (`4011` / `4012`):
To support seamless token recovery and distinct UI routing, authentication failures are categorized into discrete application error codes:

| Code | Status | Enum Identifier | Meaning & Client Action |
| :--- | :--- | :--- | :--- |
| `4011` | `error` | `TOKEN_EXPIRED` | The JWT Access Token has expired (`ExpiredJwtException`). Returned with HTTP `401`. |
| `4012` | `error` | `TOKEN_INVALID` | The token has an invalid signature, is blacklisted in Redis (`blacklist:<token>`), or has an outdated `token_version` (`JwtException`). Returned with HTTP `401`. |

**Client handling (`apiClient.js`)**: Any HTTP `401` response (including `4011` and `4012`) triggers a single, de-duplicated silent refresh via `POST /api/auth/refresh-token` (the server keeps a 15-second grace window `rt_grace:<token>` so concurrent refreshes receive the same token pair), after which the original request is retried. If the refresh fails, `notifySessionExpired()` dispatches the `chatweb:session-expired` event and the user must sign in again.

---

## 2. Comprehensive Module Catalog

### 2.1. Authentication & Session Management (`/api/auth`)

Manages user registration, credential authentication, session tokens, and security revocations.

| Method | Endpoint | Description & Security |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Initiates registration. Stores data in Redis cache for 5 minutes and sends a 6-digit OTP email. Rate limit: 3 req/min. |
| `POST` | `/api/auth/verify-account` | Validates registration OTP. Persists the user into PostgreSQL and indexes credentials into Redis Cuckoo Filters. Rate limit: 5 req/min. |
| `POST` | `/api/auth/resend-otp` | Resends account activation OTP (`?email=...`). Rate limit: 3 req/min. |
| `POST` | `/api/auth/login` | Authenticates via username/password. Performs Cuckoo Filter preflight check. Returns Access Token in `Authorization: Bearer <token>` header and sets HttpOnly `refreshToken` cookie. Rate limit: 5 req/min. |
| `POST` | `/api/auth/refresh-token` | Exchanges the `refreshToken` cookie for a new token pair using Token Rotation. Validates `token_version`. Rate limit: 30 req/min. |
| `POST` | `/api/auth/logout` | Revokes the current session: removes `refreshToken` from Redis and blacklists the active `accessToken`. |
| `POST` | `/api/auth/logout-all-devices` | Increments `token_version` in PostgreSQL ($O(1)$) to invalidate all active sessions across all devices globally. |
| `POST` | `/api/auth/forgot-password` | Initiates password reset by dispatching an OTP to the user's registered email. Rate limit: 3 req/min. |
| `POST` | `/api/auth/reset-password` | Verifies reset OTP and updates user password (increments `token_version`). Rate limit: 5 req/min. |
| `POST` | `/api/auth/resend-forgot-password`| Resends password reset OTP (`?email=...`). Rate limit: 3 req/min. |
| `GET` | `/oauth2/authorization/google` | Initiates Google OAuth2 Single Sign-On flow. |
| `GET` | `/oauth2/authorization/facebook` | Initiates Facebook OAuth2 Single Sign-On flow. |

> [!NOTE]
> **OAuth2 Success Cookie Issuance**:
> Upon successful OAuth2 authentication (`OAuth2AuthenticationSuccessHandler`), the backend generates and attaches dual HttpOnly cookies before redirecting to the frontend:
> - `accessToken`: `Path=/`, `Max-Age=15m`, `SameSite=Lax`
> - `refreshToken`: `Path=/api/auth`, `Max-Age=7d`, `SameSite=Lax`

---

### 2.2. User Management & Personal Profiles (`/api/users`)

Provides self-service profile updates, credential management, language selection, and address book operations.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/users/me` | Retrieves the account details of the currently authenticated user. |
| `GET` | `/api/users/profile` | Fetches personal profile details (names, phone, birthday, gender, avatar, language). |
| `PUT` | `/api/users/profile` | Updates personal profile fields (`firstName`, `lastName`, `phone`, `birthday`, `gender`, `language`). |
| `PATCH` | `/api/users/language` | Updates user preferred interface language (`{ "language": "vi" }`). |
| `PATCH` | `/api/users/avatar` | Uploads and updates avatar via multipart file upload. |
| `POST` | `/api/users/change-password` | Changes account password. Invalidates old session tokens. |
| `DELETE` | `/api/users/me` | Executes Hybrid Delete (`UserServiceImpl.deleteUser()`): if user has MongoDB chat history, performs **soft-delete** (`userStatus = INACTIVE`, `isOnline = false`, clears email/phone/avatar, anonymizes name to `"Tài khoản Đã xóa"`, deletes old email from `filter:emails`); if no chat history exists, performs **hard-delete** in PostgreSQL and deletes both username and email from Redis Cuckoo Filters. |
| `GET` | `/api/users/addresses` | Lists all saved addresses for the authenticated user. |
| `GET` | `/api/users/address/{addressId}` | Retrieves details for a specific address. |
| `POST` | `/api/users/address` | Adds a new address entry. |
| `PUT` | `/api/users/address/{addressId}` | Updates an existing address. |
| `DELETE` | `/api/users/address/{addressId}` | Deletes an address entry. |
| `POST` | `/api/users/initiate-email-change` | Initiates email address update; sends verification OTP to the new email. |
| `POST` | `/api/users/verify-email-change` | Validates OTP and completes email update (re-indexes Redis Cuckoo Filter). |
| `POST` | `/api/users/resend-email-verification` | Resends verification OTP for pending email update. |
| `POST` | `/api/users/initiate-phone-change` | Initiates phone number update. |
| `POST` | `/api/users/verify-phone-change` | Validates OTP and updates phone number. |
| `POST` | `/api/users/resend-phone-change-verification` | Resends verification OTP for pending phone update. |

---

### 2.3. User Search (`/api/users/search`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/users/search?keyword={q}` | Basic user search matching usernames, full names, or email addresses. Rate limit: **30 req/min**. |
| `GET` | `/api/users/search/filter` | Multi-criteria advanced search combining user attributes and address fields. Rate limit: **20 req/min**. |

---

### 2.4. Friend Management & Social Graph (`/api/friends`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/friends` | Returns paginated list of accepted friends. Rate limit: **45 req/min**. |
| `GET` | `/api/friends/requests` | Returns pending incoming friend requests. Rate limit: **30 req/min**. |
| `GET` | `/api/friends/sent` | Returns pending outgoing friend requests. Rate limit: **30 req/min**. |
| `GET` | `/api/friends/blocked` | Lists users blocked by the authenticated user. Rate limit: **30 req/min**. |
| `POST` | `/api/friends/request` | Sends a friend invitation (`{ "targetUsername": "..." }`). Dispatches real-time WebSocket alert. Rate limit: **20 req/min**. Mandatory `X-Idempotency-Key` (`@Idempotent(key = "friend_request", ttl = 300)`). |
| `POST` | `/api/friends/accept` | Accepts a friend request (`{ "targetUsername": "..." }`). Rate limit: **20 req/min**. Mandatory `X-Idempotency-Key` (`@Idempotent(key = "friend_accept", ttl = 300)`). |
| `DELETE` | `/api/friends/{username}` | Unfriends a user or declines/cancels a friend invitation. Rate limit: **20 req/min**. |
| `POST` | `/api/friends/block/{username}` | Blocks a user, preventing all messaging and friend interactions. Rate limit: **20 req/min**. |
| `POST` | `/api/friends/unblock/{username}` | Removes block status for a user. Rate limit: **20 req/min**. |

---

### 2.5. Messaging History & Moderation (`/api/messages`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/messages/private` | Cursor-based paginated chat history (`?user2={recipient}&cursor={cursor}&size={size}`). Rate limit: 45 req/min. |
| `GET` | `/api/messages/unread-counts` | Aggregates unread message counts grouped by conversation partner. Rate limit: 30 req/min. |
| `GET` | `/api/messages/search` | Searches text content within a private conversation (`?user2={recipient}&keyword={q}&cursor=&size=20`). Rate limit: 20 req/min. |
| `GET` | `/api/messages/system` | Cursor-paginated list of active system announcements (`?cursor=&size=20`). |
| `GET` | `/api/messages/{id}` | Fetches detailed metadata for an individual message. Rate limit: 60 req/min. |
| `POST` | `/api/messages/mark-as-read` | Atomic read receipt watermark upsert (`MarkReadRequest`). Updates MongoDB `$max`, evicts Redis cache, and dispatches real-time Kafka event. Rate limit: 30 req/min. |
| `POST` | `/api/messages/reaction` | Adds or updates an emoji reaction on a message (`ReactionRequest`). Rate limit: 30 req/min. |
| `PUT` | `/api/messages/edit` | Edits message text content (`EditMessageRequest`). Rate limit: 20 req/min. |
| `DELETE` | `/api/messages/revoke` | Revokes a message (`RevokeMessageRequest`). Soft deletion in MongoDB. Rate limit: 20 req/min. |

#### Specific Request & Response Schemas:

- **Mark As Read Request (`MarkReadRequest`)**:
  ```json
  {
    "sender": "bob_smith"
  }
  ```
  *(Note: `conversationId` is resolved server-side from the authenticated user and sender).*

- **Reaction Request (`ReactionRequest`)**:
  ```json
  {
    "messageId": "65e52b121f938b29c8e1a456",
    "recipient": "bob_smith",
    "reactionType": "HEART"
  }
  ```
  *Allowed `reactionType`*: `LIKE`, `HEART`, `LAUGH`, `SAD`, `ANGRY`. Passing `reactionType: null` **removes** the user's reaction from the message.

- **Edit Message Request (`EditMessageRequest`)**:
  ```json
  {
    "messageId": "65e52b121f938b29c8e1a456",
    "recipient": "bob_smith",
    "newContent": "Updated text message content"
  }
  ```

- **Revoke Message Request (`RevokeMessageRequest`)**:
  ```json
  {
    "messageId": "65e52b121f938b29c8e1a456",
    "recipient": "bob_smith"
  }
  ```

---

### 2.6. Persistent In-App Notifications (`/api/notifications`)

Manages stored user notifications (friend activity, message updates, and system alerts).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/notifications` | Cursor-paginated list of notifications (`?cursor={cursor}&size={size}`). Rate limit: 45 req/min. |
| `GET` | `/api/notifications/unread-counts`| Retrieves total count of unread notifications for badge rendering. Rate limit: 60 req/min. |
| `PATCH`| `/api/notifications/{id}/read` | Marks a specific notification as read. Rate limit: 60 req/min. |
| `PATCH`| `/api/notifications/read-all` | Marks all unread notifications of the authenticated user as read. Rate limit: 15 req/min. |

---

### 2.7. User Reporting Submissions (`/api/reports`)

Allows users to report abusive or violating behavior.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/reports` | Submits a report (`CreateReportRequest`). Supports targeting via `reportedUserId` OR `reportedUsername` (`{ "reportedUserId": 12, "reportedUsername": "bob_smith", "reason": "SPAM", "details": "..." }`). Rate limit: 10 req/min. |
| `GET` | `/api/reports/me` | Paginated list of reports filed by current user (`?page=0&size=10&sortDir=desc`). |
| `DELETE`| `/api/reports/{id}` | Cancels a pending report filed by the current user. |

---

### 2.8. Cloud Media Uploads (`/api/messages/upload`)

Processes multipart media uploads to Cloudinary storage with strict security sanitization and rate limits. Both upload endpoints are governed by `@RateLimit(limit = 10, period = 60)` (10 req/min per user) and idempotency TTL of 600 seconds.

| Method | Endpoint | Parameter & Limits | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/messages/upload/image` | `@RequestParam("image")` multipart file. JPEG, PNG, WEBP, GIF (Max 20MB). Rate limit: 10 req/min. TTL: 600s. | Validates MIME type starts with `image/` and strictly rejects `image/svg+xml` to eliminate stored SVG XSS. Uploads image and returns Cloudinary CDN URL. Supports `X-Idempotency-Key`. |
| `POST` | `/api/messages/upload/video` | `@RequestParam("video")` multipart file. MP4, MOV, WEBM (Max 20MB). Rate limit: 10 req/min. TTL: 600s. | Validates MIME type starts with `video/`. Uploads video and returns Cloudinary URL. Supports `X-Idempotency-Key`. |
| `PATCH`| `/api/users/avatar` | `@RequestParam("file")` multipart file (Max 5MB). Images only (No SVG). | Updates personal profile avatar image. |

---

### 2.9. Administrative Report Moderation (`/api/admin/reports`)

Restricted to moderators with authority `ADMIN_VIEW_REPORTS`, `ADMIN_RESOLVE_REPORTS`, `ADMIN_DELETE_REPORTS`.

| Method | Endpoint | Authority | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/reports` | `ADMIN_VIEW_REPORTS` | Search, filter, and paginate reports (`?page=&size=&sorts=`). |
| `GET` | `/api/admin/reports/statistics` | `ADMIN_VIEW_REPORTS` | Aggregates counts by report status (`PENDING`, `RESOLVED`, `DISMISSED`). |
| `GET` | `/api/admin/reports/{id}` | `ADMIN_VIEW_REPORTS` | Retrieves full details of a specific report. |
| `PUT` | `/api/admin/reports/{id}/resolve` | `ADMIN_RESOLVE_REPORTS`| Resolves/dismisses a report (`ResolveReportRequest`). |
| `DELETE`| `/api/admin/reports/{id}` | `ADMIN_DELETE_REPORTS` | Permanently deletes a report record. |

#### Resolve Report Request (`ResolveReportRequest`):
```json
{
  "status": "RESOLVED",
  "resolutionNote": "Confirmed Terms of Service violation. User has been temporarily suspended.",
  "lockReportedUser": true
}
```
*`status` values*: `RESOLVED`, `DISMISSED`. If `lockReportedUser: true`, the calling administrator **must** possess the `ADMIN_LOCK` authority (otherwise rejected with `403 Forbidden`). When authorized, the user lock and report resolution execute within a single transaction.

---

### 2.10. Role-Based Access Control (`/api/admin/roles`)

Restricted to administrative personnel. Standard system roles are **`USER`** and **`ADMIN`**.

| Method | Endpoint | Authority | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/roles` | `ADMIN_ROLE_VIEW_ALL` | Lists all defined system roles (`USER`, `ADMIN`, custom roles). |
| `GET` | `/api/admin/roles/permissions` | `ADMIN_ROLE_VIEW_ALL_PERMISSION` | Lists available permissions currently held by the authenticated admin (prevents privilege escalation). |
| `POST` | `/api/admin/roles` | `ADMIN_ROLE_ADD` | Creates a new role and maps associated permissions. |
| `PUT` | `/api/admin/roles/{id}` | `ADMIN_ROLE_UPDATE` | Modifies role name, description, or assigned permissions. |
| `DELETE`| `/api/admin/roles/{id}` | `ADMIN_ROLE_DELETE` | Deletes a custom role from the system. |

> [!NOTE]
> **`userRole` Automatic Enforcement**:
> In `RoleRequest` and `RoleResponse`, the `userRole` boolean designates whether a role is an ordinary user role. If a role is assigned any permission starting with `ADMIN_`, `AdminRoleServiceImpl` automatically enforces `userRole = false`.

---

### 2.11. Administrative Mailer (`/api/admin/emails`)

| Method | Endpoint | Authority | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/admin/emails/send` | `ADMIN_SEND_EMAIL` | Dispatches individual email directly via `EmailService`. Requires `ADMIN_SEND_EMAIL` authority. |

---

### 2.12. Administrative User Management (`/api/admin/users`)

Administrative controls for user account governance.

| Method | Endpoint | Authority | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | `ADMIN_VIEW_USERS` | Paginated search, sorting, and filtering by `keyword`, `role`, `status`, `gender`, and `authProvider`. |
| `GET` | `/api/admin/users/online` | `ADMIN_VIEW_ONLINE_USERS` | Queries real-time online users directly from Redis Sorted Set `online_users`. |
| `GET` | `/api/admin/users/{username}` | `ADMIN_VIEW_USER_DETAIL` | Retrieves complete profile and status for a specific user. |
| `POST` | `/api/admin/users` | `ADMIN_CREATE` | Admin creation of new user accounts. |
| `PUT` | `/api/admin/users/{username}` | `ADMIN_UPDATE_USER` | Admin update of user profile and role assignments. |
| `DELETE`| `/api/admin/users/{username}` | `ADMIN_DELETE_USER` | Admin account deletion. |
| `POST` | `/api/admin/users/{username}/lock` | `ADMIN_LOCK` | Suspends an account (`user_status = LOCKED`). |
| `POST` | `/api/admin/users/{username}/unlock` | `ADMIN_UNLOCK` | Re-activates a locked account. |
| `DELETE`| `/api/admin/users/{username}/avatar` | `ADMIN_DELETE_AVATAR`| Purges inappropriate avatar media. |
| `GET` | `/api/admin/users/{username}/addresses` | `ADMIN_VIEW_USER_ADDRESSES` | Lists all saved addresses for the target user -> `ApiResponse<List<AddressResponse>>`. |
| `GET` | `/api/admin/users/{username}/addresses/{addressId}` | `ADMIN_VIEW_USER_ADDRESSES` | Fetches single address record for the target user -> `ApiResponse<AddressResponse>`. |
| `PUT` | `/api/admin/users/{username}/addresses/{addressId}` | `ADMIN_UPDATE_USER_ADDRESS` | Updates an address record for the target user -> `ApiResponse<AddressResponse>`. |
| `DELETE`| `/api/admin/users/{username}/addresses/{addressId}` | `ADMIN_DELETE_USER_ADDRESS` | Deletes an address record for the target user -> `ApiResponse<Void>`. |

---

### 2.13. Spring Boot Actuator Endpoints (`/actuator`)

Operational monitoring, liveness, and Prometheus metric scraping endpoints configured in `SecurityConfig.java`:

| Method | Endpoint | Access Control | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/actuator/health` | Public (`permitAll()`) | Liveness and readiness probe for Docker / Kubernetes container orchestration. |
| `GET` | `/actuator/info` | Public (`permitAll()`) | Exposes application build information and Git commit metadata. |
| `GET` | `/actuator/prometheus` | Public (`permitAll()`) | Exposes Micrometer metrics formatted for Prometheus server scraping. |
| `GET` | `/actuator/**` | Restricted (`ADMIN_VIEW_USERS`) | Access to extended operational endpoints (metrics, env, loggers, thread dump). |
