# System Admin Dashboard — Business Flow & Quy tắc thống kê (Sprint 10)

Tài liệu mô tả business flow và các quy tắc lọc/tổng hợp dữ liệu cho cụm API `/api/v1/system-admin/dashboard/*` sau khi khắc phục các lỗi phát hiện trong quá trình System Testing (`WF05 + Dashboard`).

---

## 1. Tách biệt phạm vi thống kê (`scope`)

Hệ thống phân tách dữ liệu theo 2 trục tương ứng: **B2C / Global** và **B2B / Local**.

### 1.1. Giá trị `scope` hợp lệ
| `scope` | Ý nghĩa chuẩn hóa | Phạm vi áp dụng |
|---|---|---|
| `ALL` (mặc định nếu bỏ trống) | Tổng hợp toàn hệ thống | Giữ tương thích ngược; riêng các danh sách/kho có nguy cơ trộn lẫn vẫn tách riêng trường `global` / `local` |
| `B2C` hoặc `GLOBAL` | Chỉ dữ liệu B2C / Global | Người dùng `CUSTOMER`, đơn hàng `TIER_SUBSCRIPTION`, nội dung & quiz có `school_id IS NULL` |
| `B2B` hoặc `LOCAL` | Chỉ dữ liệu B2B / Local | Người dùng `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`, đơn hàng `SCHOOL_TOKEN_TOPUP`, nội dung & quiz có `school_id IS NOT NULL` |

Nếu `scope` khác các giá trị trên (không phân biệt hoa/thường), API trả về `400 Bad Request` (`InvalidRequestException`).

---

## 2. Quy tắc lọc dữ liệu theo từng module Dashboard

### 2.1. Loại trừ dữ liệu đã xóa mềm (`deleted_at IS NULL`)
Mọi số liệu thống kê hiện hành trên Dashboard **không tính** các bản ghi đã xóa mềm (`deleted_at IS NOT NULL`):
- **Kho nội dung (`HistoricalContext`, `Character`, `Document`)**:
  - `total`, `published`, `active` đều yêu cầu `deleted_at IS NULL`.
- **Chat (`ChatSession`, `Message`)**:
  - Đếm phiên và tin nhắn yêu cầu `cs.deleted_at IS NULL` và `m.deleted_at IS NULL`.
  - Tính tổng token tiêu thụ (`sumTokensBetween`, `sumTokensByPeriod`) yêu cầu cả `message.deleted_at IS NULL` và `chat_session.deleted_at IS NULL`.
- **Quiz (`Quiz`, `QuizSession`, `QuizAnswerDetail`)**:
  - `totalQuizzes`, `publishedQuizzes`, `draftQuizzes` chỉ xét `quiz.deleted_at IS NULL`.
  - `startedSessions`, `completedSessions`, `averageScorePercentage`, `sessionsTrend` chỉ xét `quiz_session.deleted_at IS NULL` **và** `quiz.deleted_at IS NULL`.
- **Users (`User`)**:
  - `countAllUsers`, `countCreatedBetween`, `countNewUsersByPeriod` chỉ tính `user.deleted_at IS NULL`.
  - Số user đã xóa mềm chỉ hiển thị riêng ở trường `deleted` (`countDeletedUsers` / `countDeletedUsersByRoles`).

### 2.2. Tách Global Data và Local Data
- **Nhân vật Hot nhất (`/overview`)**:
  - `topGlobalCharacters`: luôn chỉ lấy nhân vật hệ thống (`character.school_id IS NULL`).
  - `topLocalCharacters`: luôn chỉ lấy nhân vật của trường (`character.school_id IS NOT NULL`).
  - `topCharacters`: trả `topLocalCharacters` khi `scope=B2B` (hoặc `LOCAL`), và `topGlobalCharacters` khi `scope=B2C`/`GLOBAL`/`ALL` (tránh trộn nhân vật trùng tên giữa Global và Local).
- **Kho nội dung (`/content`)**:
  - `historicalContexts`, `characters`: trả Global khi `scope=ALL`/`B2C`/`GLOBAL`, trả Local khi `scope=B2B`/`LOCAL`.
  - `localHistoricalContexts`, `localCharacters`: luôn trả số liệu Local để FE có thể hiển thị song song.
- **Quiz (`/quiz`)**:
  - `GLOBAL` / `B2C`: lọc theo `historical_context.school_id IS NULL`.
  - `LOCAL` / `B2B`: lọc theo `historical_context.school_id IS NOT NULL`.
  - `topWrongQuestions` trả thêm `questionContent` (`question.content`) thay vì chỉ có `questionId` UUID.

### 2.3. Phiên chat đang hoạt động (`/chat-activity`)
- **Định nghĩa mới**: Một phiên chat được tính là **đang hoạt động** (`activeSessions`) khi và chỉ khi:
  - `deleted_at IS NULL`
  - `last_message_at IS NOT NULL`
  - `last_message_at >= NOW() - 2 minutes`
- Khi `scope=B2C` hoặc `scope=B2B`, `activeSessions` lọc thêm theo `user.role` tương ứng.

### 2.4. Doanh thu, Thanh toán & Gói dịch vụ (`/revenue`, `/payments`, `/tiers`, `/tokens`)
- **Tách doanh thu B2C và B2B**:
  - `scope=B2C`: chỉ tính `payment_order` có `order_type = 'TIER_SUBSCRIPTION'`. `averageOrderValue` và `%` đóng góp của từng gói khớp với tổng doanh thu B2C.
  - `scope=B2B`: chỉ tính `payment_order` có `order_type = 'SCHOOL_TOKEN_TOPUP'`; `revenueByTier` trả danh sách rỗng `[]`.
  - `/payments` lọc `PaymentOrder` và `PaymentTransaction` (join `payment_order`) theo `order_type` tương ứng với `scope`.
- **Chống trùng lặp gói dịch vụ (`free`, `plus`, `pro`)**:
  - Gói tháng (`no_month = 1`) và gói năm (`no_month = 12`) dùng chung `title` (`plus`, `pro`).
  - Các query `sumPaidRevenueByTier`, `countCustomerUsersByTier`, `countTokenBalanceByTier` thực hiện `GROUP BY LOWER(t.title)` và lấy `MIN(CAST(t.tier_id AS text))` làm `tierId` đại diện, gộp doanh thu / số đơn / số user của gói tháng và gói năm vào đúng 1 dòng duy nhất cho mỗi tier.
- **Tính nhất quán Token (`/tokens`)**:
  - `scope=B2C`: đồng bộ thống kê message token (`CUSTOMER`), token còn lại (`CUSTOMER`), phân bổ theo tier và top user (`CUSTOMER`).
  - `scope=B2B`: đồng bộ thống kê message token (`SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`), token còn lại của các user B2B và top user B2B; `tokenBalanceByTier` trả `[]`.
