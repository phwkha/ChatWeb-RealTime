# Tổng Quan REST API & Quy Ước Phản Hồi (REST API Overview)

Tài liệu này cung cấp cái nhìn tổng quan về hệ thống REST API, cấu trúc đóng gói dữ liệu phản hồi, quy ước mã lỗi và danh mục các phân hệ chức năng trong backend của ChatWeb.

---

## 1. Quy Ước Chung (Global Conventions)

- **Base URL**: `http://localhost:8080` (Cục bộ) hoặc `https://<domain>` (Nginx Proxy).
- **Interactive Documentation**: Swagger UI trực quan có sẵn tại:  
  👉 `http://localhost:8080/swagger-ui/index.html`
- **Định dạng dữ liệu**: `application/json; charset=UTF-8`
- **Đa ngôn ngữ (i18n)**: Thông điệp phản hồi (`message`) tự động thay đổi theo header `Accept-Language: vi-VN` hoặc `en-US`.

### Cấu Trúc Phản Hồi Chuẩn (`ApiResponse<T>`)
Tất cả các endpoint REST đều trả về một phong bì (Envelope) thống nhất:

```json
{
  "code": 200,
  "message": "Thao tác thành công",
  "data": { ... }
}
```

Khi xảy ra lỗi (Validation, Business, Security), `GlobalExceptionHandler` sẽ chặn và trả về mã lỗi cụ thể:

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
| `POST` | `/api/auth/register` | Nhận thông tin đăng ký và gửi mã OTP qua email. |
| `POST` | `/api/auth/verify-otp` | Xác minh mã OTP trong 5 phút để kích hoạt tài khoản chính thức. |
| `POST` | `/api/auth/login` | Đăng nhập bằng username/password, cấp phát JWT Cookies. |
| `POST` | `/api/auth/refresh-token` | Sử dụng Refresh Token trong Cookie để cấp mới Access Token. |
| `POST` | `/api/auth/logout` | Đăng xuất phiên hiện tại, đưa Access Token vào Redis Blacklist. |
| `POST` | `/api/auth/logout-all-devices` | Tăng `token_version` trong DB để vô hiệu hóa toàn bộ session cũ. |
| `GET` | `/oauth2/authorization/google` | Điểm bắt đầu quy trình đăng nhập bằng tài khoản Google. |

---

### 2.2. Phân Hệ Người Dùng & Hồ Sơ (`/api/users`)
Quản lý thông tin cá nhân và tài khoản.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/users/profile` | Lấy thông tin hồ sơ của người dùng hiện tại đang đăng nhập. |
| `PUT` | `/api/users/profile` | Cập nhật họ tên, ngày sinh, giới tính, số điện thoại. |
| `POST` | `/api/users/change-password` | Đổi mật khẩu tài khoản (tự động tăng `token_version`). |
| `GET` | `/api/users/{username}` | Xem thông tin công khai của một người dùng khác. |

---

### 2.3. Phân Hệ Tìm Kiếm (`/api/search`)
Tìm kiếm người dùng để kết bạn và tạo cuộc trò chuyện.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/search/users?q={query}` | Tìm kiếm tài khoản theo username, họ tên hoặc email. |

---

### 2.4. Phân Hệ Quản Lý Bạn Bè (`/api/friends`)
Thiết lập mạng lưới liên lạc giữa các thành viên.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `POST` | `/api/friends/request/{addresseeId}` | Gửi lời mời kết bạn (Bắn event sang Kafka `friend-notifications`). |
| `PUT` | `/api/friends/accept/{requestId}` | Chấp nhận lời mời kết bạn. |
| `PUT` | `/api/friends/reject/{requestId}` | Từ chối lời mời kết bạn. |
| `DELETE` | `/api/friends/{friendId}` | Hủy kết bạn (Unfriend). |
| `POST` | `/api/friends/block/{userId}` | Chặn người dùng (Không thể nhắn tin hay tìm thấy nhau). |
| `GET` | `/api/friends/list` | Lấy danh sách bạn bè hiện tại kèm trạng thái Online/Offline. |
| `GET` | `/api/friends/requests/pending` | Lấy danh sách các lời mời kết bạn đang chờ phản hồi. |

---

### 2.5. Phân Hệ Lịch Sử Tin Nhắn (`/api/messages`)
Truy xuất tin nhắn cũ và trạng thái đã đọc.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/messages/history/{recipient}` | Lấy lịch sử chat 1-1 phân trang theo mốc thời gian (Cursor-based). |
| `POST` | `/api/messages/mark-read/{conversationId}` | Đánh dấu đã đọc tin nhắn cuối cùng (Cập nhật `read_receipts`). |
| `PUT` | `/api/messages/{messageId}` | Chỉnh sửa nội dung tin nhắn (Bắn event vào `message-update`). |
| `DELETE` | `/api/messages/{messageId}` | Thu hồi tin nhắn (Xóa mềm - `isDeleted = true`). |

---

### 2.6. Phân Hệ Tải Lên Đa Phương Tiện (`/api/chat/upload`)
Tải lên hình ảnh, video và tệp đính kèm.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `POST` | `/api/chat/upload` | Tải file lên Cloudinary, trả về URL an toàn (HTTPS CDN), fileName và fileSize. |

---

### 2.7. Phân Hệ Quản Lý Khóa Mã Hóa Đầu-Cuối E2EE (`/api/keys`)
Hỗ trợ việc trao đổi khóa công khai giữa các client.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `POST` | `/api/keys/public-key` | Đăng ký Public Key RSA của client hiện tại lên máy chủ. |
| `GET` | `/api/keys/public-key/{username}` | Lấy Public Key của một người dùng bất kỳ để tiến hành mã hóa tin nhắn. |
| `POST` | `/api/keys/rsa` | Lưu trữ bản sao lưu Private Key đã được mã hóa bằng mật khẩu người dùng. |
| `GET` | `/api/keys/rsa` | Tải lại Private Key đã mã hóa khi đăng nhập từ thiết bị mới. |

---

### 2.8. Phân Hệ Quản Trị Hệ Thống (`/api/admin`)
Dành riêng cho tài khoản có vai trò `ROLE_ADMIN`.

| Phương thức | Đường dẫn API | Mô tả & Chức năng |
| :--- | :--- | :--- |
| `GET` | `/api/admin/users` | Danh sách toàn bộ tài khoản người dùng trong hệ thống. |
| `PUT` | `/api/admin/users/{userId}/lock` | Khóa tài khoản người dùng (Chuyển trạng thái sang `LOCKED`). |
| `PUT` | `/api/admin/users/{userId}/unlock` | Mở khóa tài khoản người dùng. |
| `PUT` | `/api/admin/users/{userId}/role` | Phân cấp hoặc thu hồi quyền quản trị. |
