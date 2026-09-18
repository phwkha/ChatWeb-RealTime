# REST API Overview & Integration Guide

This document provides a comprehensive reference for the ChatWeb REST API, including global response envelope formats, error handling standards, idempotency controls, and a complete catalog of all service modules.

---

## 1. Global Conventions

- **Base URLs**:
  - Local Ingress: `http://localhost`
  - Production / Staging: `https://<domain>`
- **Interactive Documentation**: OpenAPI / Swagger UI is available at:  
  👉 `http://localhost/swagger-ui/index.html`
- **Media Type**: `application/json; charset=UTF-8`
- **Internationalization (i18n)**: Response messages adapt to client preferences via the `Accept-Language` header (`en-US`, `vi-VN`, `ja-JP`).
- **Idempotency Safeguard**: For state-mutating requests (file uploads, friend request approvals), clients should provide an `X-Idempotency-Key: <UUID>` header. The backend acquires a distributed Redis lock (`idempotent:{key}:{idempotencyKey}`) to guarantee execution safety against network timeouts and retries.

### Standard Response Envelope (`ApiResponse<T>`)

Every REST response follows a structured envelope model:

```json
{
  "code": 200,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

When an exception occurs (validation error, business failure, or unauthorized access), `GlobalExceptionHandler` traps the exception and returns a standardized error payload:

```json
{
  "code": 400,
  "message": "Password confirmation does not match",
  "data": null
}
```

---

## 2. Comprehensive Module Catalog

### 2.1. Authentication & Session Management (`/api/auth`)

Manages user registration, credential authentication, session tokens, and security revocations.

| Method | Endpoint | Description & Security |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Initiates registration. Stores data in Redis cache for 5 minutes and sends a 6-digit OTP email. Rate limit: 3 req/min. |
| `POST` | `/api/auth/verify-account` | Validates registration OTP. Persists the user into PostgreSQL and indexes credentials into Redis Cuckoo Filters. |
| `POST` | `/api/auth/resend-otp` | Resends account activation OTP. Rate limit: 3 req/min. |
| `POST` | `/api/auth/login` | Authenticates via username/password. Performs Cuckoo Filter preflight check. Returns Access Token in `Authorization: Bearer <token>` header and sets HttpOnly `refreshToken` cookie. |
| `POST` | `/api/auth/refresh-token` | Exchanges the `refreshToken` cookie for a new token pair using Token Rotation. Validates `token_version`. |
| `POST` | `/api/auth/logout` | Revokes the current session: removes `refreshToken` from Redis and blacklists the active `accessToken`. |
| `POST` | `/api/auth/logout-all-devices` | Increments `token_version` in PostgreSQL ($O(1)$) to invalidate all active sessions across all devices globally. |
| `POST` | `/api/auth/forgot-password` | Initiates password reset by dispatching an OTP to the user's registered email. |
| `POST` | `/api/auth/reset-password` | Verifies reset OTP and updates user password (increments `token_version`). |
| `POST` | `/api/auth/resend-forgot-password`| Resends password reset OTP. |
| `GET` | `/oauth2/authorization/google` | Initiates Google OAuth2 Single Sign-On flow. Redirects to `/oauth2/redirect` upon successful authentication. |

---

### 2.2. User Management & Personal Profiles (`/api/users`)

Provides self-service profile updates, credential management, and address book operations.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/users/me` | Retrieves the account details of the currently authenticated user. |
| `GET` | `/api/users/profile` | Fetches personal profile details (names, birthday, gender, avatar). |
| `PUT` | `/api/users/profile` | Updates personal profile fields. |
| `PATCH` | `/api/users/avatar` | Uploads and updates avatar via multipart file upload. |
| `POST` | `/api/users/change-password` | Changes account password. Invalidates old session tokens. |
| `DELETE` | `/api/users/me` | Permanently deletes the current user's account and personal data. |
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

### 2.3. User Search (`/api/search`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/search/users?keyword={q}` | Basic user search matching usernames, full names, or email addresses. |
| `GET` | `/api/search/users/filter` | Multi-criteria advanced search combining user attributes and address fields. |

---

### 2.4. Friend Management & Social Graph (`/api/friends`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/friends` | Returns paginated list of accepted friends. |
| `GET` | `/api/friends/requests` | Returns pending incoming friend requests. |
| `GET` | `/api/friends/sent` | Returns pending outgoing friend requests. |
| `GET` | `/api/friends/blocked` | Lists users blocked by the authenticated user. |
| `POST` | `/api/friends/request` | Sends a friend invitation (`{ "targetUsername": "..." }`). Dispatches real-time WebSocket alert. |
| `POST` | `/api/friends/accept` | Accepts a friend request (`{ "targetUsername": "..." }`). Supports `X-Idempotency-Key`. |
| `DELETE` | `/api/friends/{username}` | Unfriends a user or declines/cancels a friend invitation. |
| `POST` | `/api/friends/block/{username}` | Blocks a user, preventing all messaging and friend interactions. |
| `POST` | `/api/friends/unblock/{username}` | Removes block status for a user. |

---

### 2.5. Messaging History & Moderation (`/api/messages`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/messages/private` | Cursor-based paginated chat history (`?user2={recipient}&cursor={cursor}&size={size}`). |
| `GET` | `/api/messages/unread-counts` | Aggregates unread message counts grouped by conversation partner. |
| `GET` | `/api/messages/search` | Searches text content within a private conversation (`?user2={recipient}&keyword={q}`). |
| `GET` | `/api/messages/{id}` | Fetches detailed metadata for an individual message. |
| `POST` | `/api/messages/mark-as-read` | Atomic read receipt watermark upsert (`{ "sender": "...", "conversationId": "..." }`). Updates MongoDB `$max`, evicts unread cache, and dispatches real-time Kafka event. |
| `POST` | `/api/messages/reaction` | Adds or updates an emoji reaction on a message (`{ "messageId": "...", "reaction": "..." }`). |
| `PUT` | `/api/messages/edit` | Edits message text content (`{ "messageId": "...", "content": "..." }`). |
| `DELETE` | `/api/messages/revoke` | Revokes a message (soft deletion - `{ "messageId": "..." }`). |

---

### 2.6. Cloud Media Uploads (`/api/chat`)

Processes multipart media uploads to Cloudinary storage with strict security sanitization.

| Method | Endpoint | Allowed Formats & Limits | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/chat/image` | JPEG, PNG, WEBP, GIF (Max 20MB). **SVG explicitly disallowed** to prevent Stored XSS attacks. | Uploads image and returns Cloudinary CDN URL. Supports `X-Idempotency-Key`. |
| `POST` | `/api/chat/video` | MP4, MOV, WEBM (Max 20MB). | Uploads video. Supports `X-Idempotency-Key`. |
| `POST` | `/api/chat/file` | Documents, PDF, archives (Max 20MB). | Uploads attachment file. Supports `X-Idempotency-Key`. |

---

### 2.7. Role-Based Access Control (`/api/roles`)

Restricted to administrative personnel.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/roles` | Lists all defined system roles (`ROLE_USER`, `ROLE_ADMIN`). |
| `GET` | `/api/roles/permissions` | Lists all available application permissions. |
| `POST` | `/api/roles` | Creates a new role and maps associated permissions. |
| `PUT` | `/api/roles/{id}` | Modifies role name, description, or assigned permissions. |
| `DELETE` | `/api/roles/{id}` | Deletes a role from the system. |

---

### 2.8. System Announcements & Mailer (`/api/systems`, `/api/email`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/systems/message` | Cursor-paginated list of active system announcements (`?cursor=&size=20`). |
| `POST` | `/api/email/send` | Dispatches individual email. Requires `SEND_EMAIL` permission. |

---

### 2.9. Administrative User Management (`/api/admin/users`)

Administrative controls for user account governance.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/admin/users` | Paginated search, sorting, and filtering by role, status, and gender. |
| `GET` | `/api/admin/users/online` | Queries real-time online users directly from Redis Sorted Set `online_users`. |
| `GET` | `/api/admin/users/{username}` | Retrieves complete profile and status for a specific user. |
| `POST` | `/api/admin/users` | Admin creation of new user accounts. |
| `PUT` | `/api/admin/users/{username}` | Admin update of user profile and role assignments. |
| `DELETE` | `/api/admin/users/{username}` | Admin account deletion. |
| `POST` | `/api/admin/users/{username}/lock` | Suspends an account (`user_status = LOCKED`). |
| `POST` | `/api/admin/users/{username}/unlock` | Re-activates a locked account. |
| `DELETE` | `/api/admin/users/{username}/avatar` | Purges inappropriate avatar media. |
| `*` | `/api/admin/users/{username}/addresses...`| Administrative management of any user's address book entries. |
