# Tổng Quan REST API & Quy Ước Phản Hồi (REST API Overview)

Tài liệu này cung cấp cái nhìn toàn diện về hệ thống REST API, cấu trúc đóng gói dữ liệu phản hồi, quy ước mã lỗi, tính lũy đẳng (Idempotency) và danh mục đầy đủ các phân hệ chức năng trong backend của ChatWeb.

---

## 1. Quy Ước Chung (Global Conventions)

- **Base URL**: `http://localhost:8080` (Cục bộ) hoặc `https://<domain>` (Nginx Proxy).
- **Interactive Documentation**: Swagger UI trực quan có sẵn tại:  
  👉 `http://localhost:8080/swagger-ui/index.html`
- **Định dạng dữ liệu**: `application/json; charset=UTF-8`
- **Đa ngôn ngữ (i18n)**: Thông điệp phản hồi (`message`) tự động thay đổi theo header `Accept-Language: vi-VN` hoặc `en-US`.
- **Cơ chế lũy đẳng (Idempotency)**: Đối với các thao tác nhạy cảm (tải ảnh/video, chấp nhận kết bạn), Client có thể gửi kèm header `X-Idempotency-Key: <UUID>` để Backend ngăn chặn xử lý trùng lặp trong khoảng thời gian TTL.

### Cấu Trúc Phản Hồi Chuẩn (`ApiResponse<T>`)
Tất cả các endpoint REST đều trả về một phong bì (Envelope) thống nhất:

```json
{
  "code": 200,
  "message": "Thao tác thành công",
  "data": { ... }
}
```

Khi xảy ra lỗi (Validation, Business, Security), `GlobalExceptionHandler` sẽ bắt và trả về mã lỗi cụ thể:

```json
{
  "code": 400,
  "message": "Mật khẩu xác nhận không trùng khớp",
  "data": null
}
```

---

## 2. Danh Mục Các Phân Hệ REST API

### 2.1. Phân Hệ Xác Thực & Phiên Truy Cập (`/api/auth`)
Quản lý vòng đời tài khoản và token bảo mật.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Nhận thông tin đăng ký, tạo dữ liệu tạm trong Redis và gửi mã OTP qua email. |
| `POST` | `/api/auth/verify-account` | Xác minh mã OTP trong 5 phút để kích hoạt tài khoản chính thức vào PostgreSQL. |
| `POST` | `/api/auth/resend-otp` | Gửi lại mã OTP kích hoạt tài khoản. |
| `POST` | `/api/auth/login` | Đăng nhập bằng username/password, cấp phát cặp Cookie `accessToken` (Path `/`) và Opaque `refreshToken` (UUID trong Redis, Path `/api/auth`). |
| `POST` | `/api/auth/refresh-token` | Sử dụng Refresh Token trong Cookie để cấp mới Access Token theo cơ chế Token Rotation và đối chiếu `token_version`. |
| `POST` | `/api/auth/logout` | Đăng xuất phiên hiện tại: xóa Refresh Token khỏi Redis và đưa Access Token vào Redis Blacklist. |
| `POST` | `/api/auth/logout-all-devices` | Tăng `token_version` trong PostgreSQL ($O(1)$) để vô hiệu hóa toàn bộ session cũ trên mọi thiết bị. |
| `POST` | `/api/auth/forgot-password` | Yêu cầu gửi mã OTP đặt lại mật khẩu qua email. |
| `POST` | `/api/auth/reset-password` | Đặt lại mật khẩu mới bằng mã OTP xác thực. |
| `POST` | `/api/auth/resend-forgot-password` | Gửi lại mã OTP quên mật khẩu. |
| `GET` | `/oauth2/authorization/google` | Khởi tạo luồng đăng nhập SSO bằng tài khoản Google OAuth2. |

---

### 2.2. Phân Hệ Người Dùng & Hồ Sơ (`/api/users`)
Quản lý thông tin tài khoản, hồ sơ cá nhân, địa chỉ và luồng xác minh cập nhật.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/users/me` | Lấy thông tin tài khoản hiện tại đang đăng nhập. |
| `GET` | `/api/users/profile` | Lấy chi tiết hồ sơ người dùng (họ tên, ngày sinh, giới tính, avatar). |
| `PUT` | `/api/users/profile` | Cập nhật thông tin hồ sơ cá nhân. |
| `PATCH` | `/api/users/avatar` | Tải lên và cập nhật ảnh đại diện mới qua Multipart file. |
| `POST` | `/api/users/change-password` | Đổi mật khẩu cá nhân (tự động tăng `token_version` thu hồi session cũ). |
| `DELETE` | `/api/users/me` | Xóa tài khoản cá nhân của chính mình. |
| `GET` | `/api/users/addresses` | Danh sách toàn bộ sổ địa chỉ của người dùng. |
| `GET` | `/api/users/address/{addressId}` | Chi tiết một địa chỉ cụ thể theo ID. |
| `POST` | `/api/users/address` | Thêm mới địa chỉ (số nhà, đường, phường/xã, quận/huyện, tỉnh/TP). |
| `PUT` | `/api/users/address/{addressId}` | Cập nhật thông tin địa chỉ đã có. |
| `DELETE` | `/api/users/address/{addressId}` | Xóa một địa chỉ khỏi danh sách. |
| `POST` | `/api/users/initiate-email-change` | Khởi tạo yêu cầu đổi email (gửi mã OTP đến email mới). |
| `POST` | `/api/users/verify-email-change` | Xác thực OTP để hoàn tất cập nhật email mới. |
| `POST` | `/api/users/resend-email-verification`| Gửi lại OTP xác minh email mới. |
| `POST` | `/api/users/initiate-phone-change` | Khởi tạo yêu cầu cập nhật số điện thoại. |
| `POST` | `/api/users/verify-phone-change` | Xác thực hoàn tất đổi số điện thoại. |
| `POST` | `/api/users/resend-phone-change-verification` | Gửi lại mã xác minh số điện thoại. |

---

### 2.3. Phân Hệ Tìm Kiếm (`/api/search`)
Tìm kiếm người dùng theo từ khóa và bộ lọc đa tiêu chí.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/search/users?keyword={keyword}` | Tìm kiếm người dùng cơ bản theo username, họ tên hoặc email. |
| `GET` | `/api/search/users/filter` | Tìm kiếm nâng cao kết hợp các tiêu chí người dùng (`user`) và địa chỉ (`address`). |

---

### 2.4. Phân Hệ Quản Lý Bạn Bè (`/api/friends`)
Quản lý quan hệ bạn bè, gửi lời mời và danh sách chặn.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/friends` | Lấy danh sách bạn bè hiện tại (phân trang, sắp xếp). |
| `GET` | `/api/friends/requests` | Danh sách lời mời kết bạn nhận được đang chờ phản hồi. |
| `GET` | `/api/friends/sent` | Danh sách lời mời kết bạn do chính mình đã gửi đi. |
| `GET` | `/api/friends/blocked` | Danh sách các tài khoản đang bị người dùng chặn. |
| `POST` | `/api/friends/request` | Gửi lời mời kết bạn (Body: `{ "targetUsername": "..." }`). |
| `POST` | `/api/friends/accept` | Chấp nhận lời mời kết bạn (Body: `{ "targetUsername": "..." }`, hỗ trợ `X-Idempotency-Key`). |
| `DELETE` | `/api/friends/{username}` | Hủy kết bạn hoặc thu hồi/từ chối lời mời kết bạn. |
| `POST` | `/api/friends/block/{username}` | Chặn người dùng theo username. |
| `POST` | `/api/friends/unblock/{username}` | Bỏ chặn người dùng. |

---

### 2.5. Phân Hệ Lịch Sử Tin Nhắn (`/api/messages`)
Truy xuất tin nhắn, tìm kiếm, đánh dấu đã đọc và phản hồi emoji.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/messages/private` | Lấy lịch sử chat 1-1 dạng con trỏ (`?user2={recipient}&cursor={cursor}&size={size}`). |
| `GET` | `/api/messages/unread-counts` | Thống kê số lượng tin nhắn chưa đọc phân nhóm theo từng người gửi. |
| `GET` | `/api/messages/search` | Tìm kiếm nội dung tin nhắn trong cuộc trò chuyện (`?user2={recipient}&keyword={keyword}`). |
| `GET` | `/api/messages/{id}` | Truy vấn chi tiết một tin nhắn cụ thể theo ID. |
| `POST` | `/api/messages/mark-as-read` | Đánh dấu đã đọc tin nhắn (Body: `{ "sender": "...", "conversationId": "..." }`). |
| `POST` | `/api/messages/reaction` | Thả reaction emoji vào tin nhắn (Body: `{ "messageId": "...", "reaction": "..." }`). |
| `PUT` | `/api/messages/edit` | Chỉnh sửa nội dung tin nhắn đã gửi (Body: `{ "messageId": "...", "content": "..." }`). |
| `DELETE` | `/api/messages/revoke` | Thu hồi tin nhắn (Xóa mềm - Body: `{ "messageId": "..." }`). |

---

### 2.6. Phân Hệ Tải Lên Đa Phương Tiện (`/api/chat`)
Tải tệp media lên Cloudinary có kiểm tra định dạng, giới hạn kích thước và bảo đảm tính lũy đẳng.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `POST` | `/api/chat/image` | Tải ảnh chat (Multipart, max 20MB, hỗ trợ `X-Idempotency-Key`). |
| `POST` | `/api/chat/video` | Tải video chat (Multipart, max 20MB, hỗ trợ `X-Idempotency-Key`). |
| `POST` | `/api/chat/file` | Tải tệp đính kèm tài liệu (Multipart, hỗ trợ `X-Idempotency-Key`). |

---

### 2.7. Phân Hệ Quản Lý Vai Trò & Quyền Hạn (`/api/roles`)
Dành cho quản trị viên hệ thống để kiểm soát phân quyền RBAC.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/roles` | Lấy danh sách toàn bộ các vai trò (`ROLE_USER`, `ROLE_ADMIN`, ...). |
| `GET` | `/api/roles/permissions` | Lấy danh sách tất cả quyền hạn có trong hệ thống. |
| `POST` | `/api/roles` | Tạo mới vai trò và gán quyền hạn. |
| `PUT` | `/api/roles/{id}` | Cập nhật tên, mô tả hoặc danh sách quyền hạn của vai trò. |
| `DELETE` | `/api/roles/{id}` | Xóa một vai trò khỏi hệ thống. |

---

### 2.8. Phân Hệ Tin Nhắn Hệ Thống & Email (`/api/systems`, `/api/email`)

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/systems/message` | Lấy danh sách thông báo hệ thống phát từ Ban Quản Trị theo cursor (`?cursor=&size=20`). |
| `POST` | `/api/email/send` | Gửi email đơn lẻ (yêu cầu quyền `SEND_EMAIL`). |

---

### 2.9. Phân Hệ Quản Trị Người Dùng (`/api/admin/users`)
Quản trị tài khoản người dùng, trạng thái khóa và dữ liệu liên quan.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/admin/users` | Lấy danh sách, tìm kiếm và lọc người dùng theo vai trò, trạng thái, giới tính. |
| `GET` | `/api/admin/users/online` | Lấy danh sách tài khoản đang online trực tiếp từ Redis ZSet. |
| `GET` | `/api/admin/users/{username}` | Xem thông tin chi tiết một người dùng bất kỳ. |
| `POST` | `/api/admin/users` | Admin tạo mới tài khoản người dùng. |
| `PUT` | `/api/admin/users/{username}` | Admin cập nhật thông tin tài khoản người dùng. |
| `DELETE` | `/api/admin/users/{username}` | Admin xóa người dùng khỏi hệ thống. |
| `POST` | `/api/admin/users/{username}/lock` | Khóa tài khoản người dùng (`user_status = LOCKED`). |
| `POST` | `/api/admin/users/{username}/unlock` | Mở khóa tài khoản người dùng. |
| `DELETE` | `/api/admin/users/{username}/avatar` | Xóa ảnh đại diện của tài khoản người dùng vi phạm. |
| `GET/POST/PUT/DELETE` | `/api/admin/users/{username}/addresses...` | Quản lý sổ địa chỉ của một người dùng bất kỳ. |
