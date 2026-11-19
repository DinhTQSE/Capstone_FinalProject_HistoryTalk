# System Admin Dashboard — Changelog & Hướng dẫn FE cập nhật (Sprint 10)

Tài liệu này tóm tắt **BE đã sửa gì** từ các lỗi trong `System Testing.docx (WF05 + Dashboard)` và **FE cần sửa/cập nhật những gì** tương ứng trên UI.

---

## 1. Tổng hợp thay đổi theo từng lỗi (BE -> FE)

| # | Vấn đề trong System Testing | BE đã sửa gì | FE cần sửa / cập nhật gì |
|---|---|---|---|
| 1 | **Tách Dashboard B2B và B2C riêng, không thống kê chung** | Thêm query param `?scope=ALL \| B2C \| B2B \| GLOBAL \| LOCAL` (mặc định `ALL`) trên `/overview`, `/users`, `/content`, `/chat-activity`, `/revenue`, `/payments`, `/quiz`, `/tokens`. Thêm `schoolAdmins`, `teachers`, `schoolStudents` vào `roles` ở `/overview`. | Thêm bộ lọc/tab **B2C** vs **B2B** trên màn hình System Admin Dashboard, truyền `?scope=B2C` hoặc `?scope=B2B` khi gọi API thay vì gọi chung không có `scope`. Hiển thị thêm 3 role B2B từ `overview.roles`. |
| 2 | **Kho nội dung tính cả phần đã xóa mềm** | `HistoricalContextRepository`, `CharacterRepository`, `DocumentRepository` đều đã lọc `deleted_at IS NULL` cho `total`, `published`, `active`. | **Không cần sửa logic tính**, dữ liệu `/content` và `overview.content` tự động khớp. |
| 3 | **Chia Global data và Local data riêng (Kho nội dung & Nhân vật Hot nhất)** | - `/overview`: bổ sung `topGlobalCharacters` (`school_id IS NULL`) và `topLocalCharacters` (`school_id IS NOT NULL`); `topCharacters` trả Global (khi `ALL`/`B2C`) hoặc Local (khi `B2B`).<br>- `/content`: bổ sung `localHistoricalContexts`, `localCharacters`. | - Card **Nhân vật Hot nhất**: có thể dùng `topGlobalCharacters` và `topLocalCharacters` để tách 2 tab/khối **Global** vs **Local**, hoặc truyền `?scope=GLOBAL` / `?scope=LOCAL`.<br>- Card **Kho nội dung**: có thể hiển thị thêm `localHistoricalContexts` & `localCharacters` cho khối B2B/Local. |
| 4 | **Doanh thu & Phân tích hội viên bị đúp gói `plus`, `pro` và lệch `%` / trung bình đơn** | - Gộp gói tháng (`no_month=1`) và gói năm (`no_month=12`) theo `LOWER(t.title)` trong `revenueByTier`, `usersByTier`, `purchasesByTier`, `tokenBalanceByTier` -> chỉ còn 3 dòng `free`, `plus`, `pro`.<br>- Tách doanh thu `TIER_SUBSCRIPTION` (`scope=B2C`) khỏi `SCHOOL_TOKEN_TOPUP` (`scope=B2B`). | - Khi xem tab **Doanh thu B2C**, gọi `/revenue?scope=B2C`. Lưu ý `revenueByTier` lọc theo khoảng `from..to` (mặc định 30 ngày gần nhất) còn `summary.totalRevenue` / `averageOrderValue` tính toàn thời gian -> nếu tính `%` đóng góp từng gói theo kỳ lọc, FE chia cho `sum(revenueByTier.revenue)` và kiểm tra lại `from`/`to` đang truyền.<br>- Khi xem tab **Doanh thu B2B** (`/revenue?scope=B2B`), `revenueByTier` trả `[]` -> **ẩn card "Doanh thu theo Gói dịch vụ"**.<br>- Ở card **Phân tích Hội viên** (`/tiers`), API chỉ trả `users`, không trả `%` -> FE sửa công thức `%` thành `users / (currentFreeUsers + currentPaidUsers) * 100` (tránh chia nhầm mẫu số). |
| 5 | **Quiz Đang Hot tổng hợp cả quiz xóa mềm & Câu hỏi hay sai nhất hiện UUID** | - `QuizRepository.countAllQuizzes()` và các query `QuizSessionRepository` đã lọc `quiz.deleted_at IS NULL`.<br>- Tách quiz Global (`school_id IS NULL`) và Local (`school_id IS NOT NULL`) theo `scope`.<br>- `topWrongQuestions` bổ sung field `questionContent`. | - **Bắt buộc sửa UI**: ở danh sách **Câu hỏi hay sai nhất**, hiển thị `item.questionContent` (fallback `item.questionId` nếu null) thay vì hiển thị UUID `item.questionId` như hiện tại.<br>- Truyền `?scope=GLOBAL` hoặc `?scope=LOCAL` nếu tách tab Quiz Global / Local. |
| 6 | **Phiên chat đang hoạt động đếm sai (41/45)** | `activeSessions` trong `/chat-activity` đã đổi sang đếm phiên có `deleted_at IS NULL` và `last_message_at >= NOW() - 2 minutes` (có lọc theo `scope`). | Cập nhật label/tooltip trên UI cho **Phiên đang hoạt động** (ví dụ: *"Có tin nhắn trong 2 phút gần nhất"*) và truyền `?scope=B2C` / `?scope=B2B` theo tab. |
| 7 | **Token toàn hệ thống thiếu nhất quán** | `/tokens` lọc đồng bộ cả `summary.totalTokens`, `remainingTokens`, `topUsersByTokenUsage` theo `scope` (`B2C` chỉ tính `CUSTOMER`, `B2B` chỉ tính user trường học). | Gọi `/tokens?scope=B2C` ở tab B2C và `/tokens?scope=B2B` ở tab B2B. Ở tab B2B, `tokenBalanceByTier` trả `[]` -> **ẩn bảng phân bổ theo Tier**. |
| 8 | **Bê cấu trúc System Admin Dashboard sang School Admin Dashboard (fit theo B2B & từng trường)** | Bổ sung bộ 6 sub-endpoints dưới `/api/v1/school-admin/dashboard/*`: `/overview`, `/users`, `/content`, `/chat-activity`, `/quiz`, `/tokens` dùng chung cấu trúc DTO với System Admin Dashboard.<br>- **Nhân vật & Kho nội dung**: School Admin chỉ lấy dữ liệu **Local của trường** (`topGlobalCharacters = []`, `topCharacters = topLocalCharacters`).<br>- **Thay Doanh thu bằng Quản lý Token của trường**: `/api/v1/school-admin/dashboard/tokens` trả về `schoolTokenManagement`, `tokenUsageByRole`, `topClassroomsByTokenUsage`, `topUsersByTokenUsage`, `trend`, `summary`. | - FE tái sử dụng layout/components của System Admin Dashboard cho màn hình **School Admin Dashboard**, đổi base URL từ `/api/v1/system-admin/dashboard/*` sang `/api/v1/school-admin/dashboard/*`.<br>- Ở School Admin: chỉ hiển thị nhân vật & nội dung **Local của trường**.<br>- Thay khu vực **Doanh thu / Hội viên** của System Admin bằng khu vực **Quản lý Quỹ Token Trường** lấy từ `/api/v1/school-admin/dashboard/tokens` (`data.schoolTokenManagement`, `data.tokenUsageByRole`, `data.topClassroomsByTokenUsage`) hoặc `overview.schoolTokenOverview`. Xem chi tiết tại [school_admin_dashboard_api_contract.md](./school_admin_dashboard_api_contract.md). |

---

## 2. Chi tiết các field mới / thay đổi trong DTO

### 2.1. `GET /api/v1/system-admin/dashboard/overview`
- `roles` có thêm:
  - `schoolAdmins: number`
  - `teachers: number`
  - `schoolStudents: number`
- Root `data` có thêm:
  - `topGlobalCharacters: TopCharacterOverview[]`
  - `topLocalCharacters: TopCharacterOverview[]`

### 2.2. `GET /api/v1/system-admin/dashboard/content`
- Root `data` có thêm:
  - `localHistoricalContexts: { total, published, active }`
  - `localCharacters: { total, published, active }`

### 2.3. `GET /api/v1/system-admin/dashboard/quiz`
- Mỗi phần tử trong `topWrongQuestions` có thêm:
  - `questionContent: string` (nội dung câu hỏi để hiển thị lên UI thay cho `questionId`).

---

## 3. Checklist test nhanh cho FE sau khi tích hợp

1. Chuyển qua lại giữa tab/filter **B2C** (`?scope=B2C`) và **B2B** (`?scope=B2B`):
   - Doanh thu B2C: tổng doanh thu = tổng tiền các gói trong `revenueByTier`, tổng `%` = 100%, `averageOrderValue` khớp.
   - Doanh thu B2C & Phân tích Hội viên: chỉ có `free`, `plus`, `pro` (mỗi gói 1 dòng, không đúp).
   - Nhân vật Hot nhất: không bị trùng nhân vật giữa Global và Local.
   - Quiz Đang Hot: `Tổng số Quiz` không tính quiz đã xóa mềm; `Câu hỏi hay sai nhất` hiển thị nội dung câu hỏi (`questionContent`).
   - Thống kê Hội thoại Chat: `Phiên đang hoạt động` chỉ đếm phiên có tin nhắn trong 2 phút gần nhất.
