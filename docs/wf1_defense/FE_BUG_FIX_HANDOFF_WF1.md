# Danh sách Lỗi & Cần Sửa Phía Frontend (WF-01) — Handoff cho Team FE

> **Người tổng hợp:** Trần Quốc Dinh (`DinhTQSE`)  
> **Phạm vi kiểm thử:** Kiểm thử E2E trực tiếp trên Production (`https://historytalk.online`) + Audit code FE (`master` @ `6501bb4`) và BE (`main` @ `658cbba`).  
> **Mục đích:** Gửi team FE sửa dứt điểm các lỗi/lệch UX-UI còn lại thuộc **WF-01** (*B2B School Onboarding, Classroom Setup & Bulk Student Enrollment*) và các màn hình liên quan trước buổi bảo vệ Hội đồng.

---

## 1. Các lỗi Backend đã được sửa xong (FE không cần workaround phía BE)

Trước khi sửa FE, lưu ý các lỗi sau **đã được vá trực tiếp ở Backend** (chờ deploy lên server):

| Vấn đề đã phát hiện trên Production | Đã sửa ở Backend | Ghi chú cho FE |
|---|---|---|
| `/staff/admin/payment/history` chết **HTTP 500 NPE** khi có đơn `SCHOOL_TOKEN_TOPUP` (`tier_id = null`) | `PaymentService` (`getAllPaymentHistory`, `getMyPaymentHistory`) null-safe `order.getTier()` và trả `tierTitle = "Nạp quỹ token trường — ..."` | Bảng admin giờ nhận `tierId: null`, `tierTitle` có chữ tiếng Việt. |
| Nút **`Năm` (`granularity=year`)** trên 2 Dashboard trả **HTTP 400** | `SystemDashboardServiceImpl`, `AnalyticsDashboardServiceImpl` & 6 Repositories đã hỗ trợ `bucket = "year"` | FE giữ nguyên nút `Năm` sẽ không còn bị lỗi `400`. |
| Giờ tạo đơn trong `Lịch sử mua token` (`/school/tokens`) bị hiển thị UTC (`10:40` thay vì `17:40`) | `PaymentService` chuẩn hóa trả ISO-8601 UTC có hậu tố `'Z'` (`...T10:40:00Z`) cho `createdAt`/`paidAt`/`expiredAt` | Khi deploy BE mới, `formatDateTime` ở FE tự parse đúng UTC -> UTC+7. |
| Tạo trường trực tiếp trả `defaultDailyTeacherQuota: null` | `SchoolManagementServiceImpl.mapToSchoolResponse` đã map đủ `defaultDailyTeacherQuota`, `maxStudents`, `maxTeachers`, `baseCustomTokenQuota` | Dùng ngay cho đồng bộ hạn mức GV. |
| Tài khoản bị khóa / tốt nghiệp đăng nhập báo `"Bạn cần đăng nhập để truy cập tài nguyên này"` (`401`) | `AuthServiceImpl.login` bắt `DisabledException` / `LockedException` và trả `ACCOUNT_DEACTIVATED: Tài khoản đã bị vô hiệu hóa` | **FE vẫn cần sửa `AuthForm.tsx:233`** (xem mục **P0-4** bên dưới). |
| `POST /api/v1/auth/logout-all` xóa nhầm cờ `mustChangePassword` | Đã xóa `user.setMustChangePassword(false)` khỏi `logoutAll` | An toàn luồng đổi mật khẩu lần đầu. |
| School Admin bị chặn tự gia hạn khi trường `EXPIRED` | `SchoolSubscriptionGuardInterceptor` đã mở ngoại lệ cho `SCHOOL_ADMIN` gọi `/school-admin/subscription/renew`, `/my-school`, `/users/me`, `/users/me/password` | Không thay đổi flow FE. |

---

## 2. Danh sách Lỗi & Thiếu sót FE Cần Sửa (Theo Mức Độ Ưu Tiên)

### P0 — Bắt buộc sửa trước ngày Bảo vệ (Ảnh hưởng trực tiếp Demo Hội đồng)

#### [FE-WF1-01] Trang `/school/tokens` reset hiển thị hạn mức HS & GV về `5.000` sau khi lưu / reload
- **File liên quan:**
  - `src/app/school/tokens/page.tsx` (dòng `37`)
  - `src/app/school/_components/token-policy.tsx` (dòng `30-36`)
  - `src/services/analytics-dashboard.service.ts` (type `SchoolAdminDashboard` dòng `20-56`)
- **Hiện trạng thực tế:**
  - Khi School Admin đổi hạn mức ngày HS thành `2.000` và GV thành `4.000`, BE trả `200 OK` và lưu đúng (`/school` Dashboard vẫn hiển thị `2.000 / 4.000`), nhưng trên `/school/tokens` ô `Mỗi học sinh` nhảy lại `5.000` ngay sau khi lưu, và sau khi `F5` reload trang cả 2 thẻ `Mỗi học sinh` và `Mỗi giáo viên` đều hiển thị `5.000`.
  - Nguyên nhân: `SchoolTokensPage` chỉ truyền `currentDefault={dashboard.data?.defaultDailyStudentQuota}` (không truyền `defaultDailyTeacherQuota`), còn `DailyQuotaPolicyPanel` đặt `useState(DEFAULT_TEACHER_QUOTA)` (`5000`) với comment cũ *"No API returns the teacher default yet"* dù BE `GET /school-admin/dashboard` và `GET /school-admin/dashboard/tokens` **đã trả sẵn `defaultDailyTeacherQuota`**. Ngoài ra query key sau mutation `useUpdateTokenPolicy` cần invalidate `school-admin/dashboard` để `currentDefault` cập nhật tức thì.
- **Cách sửa đề xuất:**
  1. Bổ sung `defaultDailyTeacherQuota?: number` vào type `SchoolAdminDashboard` (`src/services/analytics-dashboard.service.ts`).
  2. Truyền cả `currentStudentDefault={dashboard.data?.defaultDailyStudentQuota}` và `currentTeacherDefault={dashboard.data?.defaultDailyTeacherQuota}` vào `DailyQuotaPolicyPanel`.
  3. Dùng trực tiếp giá trị từ API (`currentTeacherDefault ?? DEFAULT_TEACHER_QUOTA`) thay cho `useState(DEFAULT_TEACHER_QUOTA)` hoặc sync bằng prop/effect, đồng thời đảm bảo `useUpdateTokenPolicy` invalidate đúng query của `useSchoolAdminDashboard`.
- **Tiêu chí kiểm tra xong:** Đổi HS -> `2.000`, GV -> `4.000`, bấm `F5` tại `/school/tokens`, cả 2 thẻ vẫn giữ đúng `2.000` và `4.000`.

---

#### [FE-WF1-02] Nhập CSV từ màn hình chi tiết lớp (`/school/classes/[classId]`): Re-upload báo sai `"Tài khoản mới"` + nút `"Xong"` không reset modal + lệch số dòng lỗi
- **File liên quan:**
  - `src/components/saas/class-roster-manager.tsx` (dòng `537-550`, `597-600`, `640-645`, `678`, `786`)
- **Hiện trạng thực tế (đã tái hiện trên Production):**
  1. **Báo sai `"Tài khoản mới"` khi re-upload HS đã có trong lớp:** Hàm `isExistingAccount` (dòng `549`) kiểm tra `(a.allocatedToken ?? 0) === 0 && /^\(.*\)$/.test(a.initialPassword ?? "")`. Nhưng BE trả `allocatedToken = student.getToken()` (ví dụ `5000` hoặc `2000`, khác `0`) và `initialPassword = "(Đã có trong lớp từ trước)"` → `isExistingAccount` trả `false`, làm UI hiển thị **`Tài khoản mới: 5`** và hiện nút **`Phiếu tài khoản mới`** dù không tạo tài khoản mới nào!
  2. **Không cảnh báo trùng HS đã có trong lớp trước khi gửi:** Khi re-upload cùng file vào lớp `10A1`, Preview Grid vẫn hiện `Hợp lệ (5) / Có lỗi (0)` và khi nhập vẫn toast `"Đã thêm 5 học sinh vào lớp"`. (Cần kiểm tra lại `lookupStudent` & `statusMap` ở dòng `537-588`, đồng thời tách riêng số HS `"Đã có trong lớp từ trước"` trên màn hình kết quả dựa theo `initialPassword === "(Đã có trong lớp từ trước)"`).
  3. **Bấm `"Xong"` không reset modal (dòng `786`):** Nút `"Xong"` gọi `onOpenChange(false)` trực tiếp của prop cha nên hàm `reset()` bên trong Radix `Dialog.onOpenChange` (dòng `642-645`) bị bỏ qua. Lần sau mở lại `"Nhập CSV"` vẫn hiện màn hình kết quả cũ!
  4. **Lệch số dòng lỗi (dòng `678`):** `<ImportErrorsTable errors={result.errors} session={{ sentLines: [] }} />` truyền mảng rỗng nên số dòng lỗi trả về từ BE bị lệch so với số dòng trong file CSV gốc nếu người dùng bỏ tick vài dòng ở Preview.
- **Cách sửa đề xuất:**
  1. Sửa `isExistingAccount`: chỉ cần `/^\(.*\)$/.test((a.initialPassword ?? "").trim())` (hoặc kiểm tra `initialPassword.startsWith("(")`), không phụ thuộc `allocatedToken === 0`. Phân loại rõ 3 nhóm trên UI kết quả: *Thêm mới vào lớp*, *Tài khoản mới*, *Đã có trong lớp (bỏ qua)*.
  2. Sửa nút `"Xong"` (dòng `786`): gọi `reset(); onOpenChange(false);`.
  3. Lưu `sentLines` (mảng số dòng gốc `row.line`) giống `student-import-wizard.tsx` và truyền vào `ImportErrorsTable`.
  4. Đảm bảo `lookupStudent` (dòng `537-546`) tìm chính xác theo `studentCode` / `email` và khi đóng/nhập xong invalidate cache `roster-import-lookup`.
- **Tiêu chí kiểm tra xong:** Re-upload CSV vào lớp đã có HS → Preview cảnh báo `"Học sinh đã có trong lớp này"`; bấm `"Xong"` rồi mở lại `"Nhập CSV"` hiện màn hình chọn file trống; kết quả không báo sai `"Tài khoản mới"`.

---

#### [FE-WF1-03] Chi tiết trường 404 (`/staff/admin/schools/00000000-0000-0000-0000-000000000000`) treo Skeleton thay vì hiện EmptyState ngay (`SA-SCH-04`)
- **File liên quan:**
  - `src/app/staff/admin/schools/[schoolId]/page.tsx` (dòng `25-27`, `53-72`)
  - `src/features/saas/hooks-api.ts` (hook `useApiSchool`)
- **Hiện trạng thực tế:** API `GET /api/v1/admin/schools/00000000-...` trả HTTP `404` ngay, nhưng màn hình đứng ở trạng thái skeleton loading kéo dài/treo thay vì render `<EmptyState title="Không tìm thấy trường" />`. Nguyên nhân thường do React Query mặc định `retry: 3` với backoff khi gặp `404` hoặc `AdminSchoolDetailPage` giữ `fallback` skeleton quá lâu.
- **Cách sửa đề xuất:** Trong `useApiSchool` (và các detail query trả `404`/`400`), cấu hình `retry: (failureCount, error) => apiErrorStatus(error) !== 404 && apiErrorStatus(error) !== 400 && failureCount < 2`.
- **Tiêu chí kiểm tra xong:** Truy cập `/staff/admin/schools/00000000-0000-0000-0000-000000000000` hiển thị ngay màn hình `"Không tìm thấy trường"` + nút quay về danh sách trường.

---

#### [FE-WF1-04] Thông báo lỗi đăng nhập khi khóa Giáo viên / School Admin lại ghi lý do của Học sinh
- **File liên quan:**
  - `src/components/auth/AuthForm.tsx` (dòng `230-236`)
- **Hiện trạng thực tế:** Khi `code === "ACCOUNT_DEACTIVATED"`, `AuthForm.tsx:233` hardcode chuỗi:
  `"Tài khoản đã ngừng hoạt động do học sinh đã tốt nghiệp hoặc chuyển trường. Liên hệ nhà trường nếu cần hỗ trợ."`
  Nếu Giáo viên, School Admin hoặc Customer bị khóa tài khoản và đăng nhập, UI lại thông báo *"do học sinh đã tốt nghiệp"* — không hợp ngữ cảnh.
- **Cách sửa đề xuất:** Dùng thông báo trung tính theo role hoặc hiển thị `stripErrorCode(message)` từ BE (ví dụ: `"Tài khoản đã bị vô hiệu hóa hoặc ngừng hoạt động. Vui lòng liên hệ quản trị viên nhà trường hoặc HistoryTalk."`).

---

### P1 — Nên sửa sớm để hoàn thiện toàn bộ System Testing & Ma trận RBAC

#### [FE-WF1-05] Thiếu thông báo định dạng file `MSG27` khi import CSV thiếu cột bắt buộc ở màn hình lớp (`/school/classes/[classId]`)
- **File liên quan:**
  - `src/components/saas/class-roster-manager.tsx` (dòng `610-615`) vs `src/app/school/_components/student-import-wizard.tsx` (dòng `531-571`)
- **Hiện trạng:** Ở `/school/students`, upload file thiếu cột bắt buộc (`full_name`, `dob`) sẽ hiện banner đỏ `"File không đúng định dạng danh sách học sinh"` (`MSG27`). Ở màn hình lớp (`/school/classes/[classId]`), cùng file đó lại nhảy vào Preview Grid báo lỗi từng dòng (`Có lỗi (2)`), gây không nhất quán giữa 2 nơi nhập cùng 1 nghiệp vụ.
- **Cách sửa đề xuất:** Nếu file không có cả `full_name` lẫn `dob` (và không có cột `student_code`/`email` khớp với dữ liệu hợp lệ, hoặc thiếu cột bắt buộc khi `existingAllowed` không có cột định danh), hiển thị banner cảnh báo sai mẫu file kèm nút `Tải file mẫu` & `Tự ghép cột` tương tự `student-import-wizard.tsx`.

#### [FE-WF1-06] Bấm nút Back trên trình duyệt sau khi Đăng xuất vẫn hiển thị vỏ trang Admin (`/staff/admin/*`)
- **File liên quan:** Layout bảo vệ / Auth guard phía client (`src/app/staff/layout.tsx`, `src/app/school/layout.tsx`, `src/components/auth/ForcedPasswordChangeGuard.tsx` hoặc hook auth).
- **Hiện trạng thực tế:** Sau khi đăng xuất về `/login`, bấm Back trên trình duyệt quay lại `/staff/admin/payment/history` vẫn vẽ khung trang Admin với bảng rỗng và thông báo `"Không thể tải lịch sử giao dịch"` thay vì đẩy ngay về `/login` (do bfcache / client route history). Khi vào trực tiếp URL thì middleware chặn đúng.
- **Cách sửa đề xuất:** Trong client layout của `/staff` và `/school` (hoặc global auth listener + `pageshow` event khi restore từ bfcache), nếu `!isAuthenticated` ở client store / cookie thì gọi `router.replace("/login")` ngay và không render children.

#### [FE-WF1-07] Thiếu nút Khóa / Tạm ngưng trường thủ công ở System Admin (`SA-SCH-05`)
- **File liên quan:** `src/app/staff/admin/schools/page.tsx` & `src/components/saas/admin-school-tabs.tsx`
- **Hiện trạng:** Hiện UI System Admin chỉ có `Gia hạn năm học tới`, `Kích hoạt năm học mới`, `Nạp thêm token`. Không có thao tác chuyển trạng thái trường sang `SUSPENDED` / `INACTIVE` để test case `SA-SCH-05` (*Khóa trường từ System Admin, GV/HS bị bật modal chặn ngay lập tức*). *(Lưu ý: cần phối hợp thêm endpoint BE `PUT /api/v1/admin/schools/{id}/status` nếu nhóm quyết định có tính năng khóa trường thủ công; hoặc thống nhất với team để ghi rõ trong tài liệu test là khóa tự động theo hạn gói 31/05).*

#### [FE-WF1-08] System Admin Dashboard (`/staff/admin`): Phần Token chưa nối API Sprint 10 mới (`/system-admin/dashboard/tokens`)
- **File liên quan:** `src/app/staff/admin/page.tsx` (dòng `316-319`), `src/features/admin/hooks.ts` (đã có sẵn `useAdminTokenAnalytics` nhưng chưa dùng trong `page.tsx`).
- **Hiện trạng:** `page.tsx` vẫn đọc số liệu Token từ API cũ `/admin/dashboard` (không nhận `?scope=B2B|B2C`, không cập nhật theo khoảng ngày `from/to`), mặc dù FE đã viết sẵn service & hook `useAdminTokenAnalytics(rangeParams)`.
- **Cách sửa đề xuất:** Kết nối `useAdminTokenAnalytics({ from, to, granularity, scope })` vào khối Token của `/staff/admin/page.tsx` theo đúng [system_dashboard_fe_migration_guide.md](file:///c:/Users/trand/OneDrive/Documents/Semester%207/SWD392/SWD392_FinalProject_Git/docs/sprints/sprint_10/fe_contracts/system_dashboard_fe_migration_guide.md).

#### [FE-WF1-09] Trang Quản lý Tài khoản (`/staff/admin/accounts/[role]`) thiếu phân trang (>100 user) & thiếu menu role trường
- **File liên quan:** `src/app/staff/admin/accounts/[role]/page-content.tsx` (dòng `150`), `src/features/admin/hooks.ts` (`useAdminUsers`), `src/routers/sidebar.ts`.
- **Hiện trạng:**
  - `useAdminUsers` gọi cứng `GET /admin/users?page=1&size=100` (lấy hỗn hợp mọi role rồi mới lọc client-side `u.role === role`), khiến `/accounts/customer` hiện `54` người (lệch với `64` B2C trên Dashboard) và nếu hệ thống vượt 100 user sẽ bị mất user ở trang sau.
  - Cột `Trạng thái` ở `1440px` bị cắt chữ nhẹ (`Trạng t`, `Hoạt`).
  - Breadcrumb `/staff/admin/accounts` & `/staff/admin/payment` sinh link prefetch **HTTP 404** vì không có `page.tsx` tại 2 route cha này.

#### [FE-WF1-10] Trang `/staff/admin/subscriptions` hiển thị nhãn gây hiểu nhầm `"Doanh thu tổng gói"` & `"Tổng token pool"`
- **File liên quan:** `src/app/staff/admin/subscriptions/page-content.tsx` (dòng `374-375`, `417-427`)
- **Hiện trạng:** Thẻ `"Doanh thu tổng gói: 912.800 ₫"` thực chất đang cộng giá niêm yết của 5 tier (`0 + 29.500 + 52.000 + 300.900 + 530.400`), còn `"Tổng token pool: 31,500"` đang cộng hạn mức token/ngày của 5 tier — dễ làm hội đồng tưởng nhầm là doanh thu thực tế (`1.235.000 ₫`) hoặc pool B2B.
- **Cách sửa đề xuất:** Đổi nhãn thành `"Tổng giá niêm yết các gói"` / `"Tổng hạn mức ngày các gói"` hoặc lấy số liệu doanh thu thực từ `/system-admin/dashboard/revenue?scope=B2C`.

---

### P2 — Cải thiện UX/UI & Validation phụ

1. **Ẩn cột `phoneNumber` trong Preview Grid dù có validate lỗi (`student-import-preview.tsx:106-112` vs `student-import.ts:296`):** Nếu SĐT > 20 ký tự, dòng bị đánh dấu lỗi và khóa checkbox nhưng người dùng không thấy cột SĐT để sửa trực tiếp. Cần thêm cột `phoneNumber` vào `PREVIEW_FIELDS` (hoặc hiển thị inline).
2. **Tạo lớp không khớp khối (`11A9` với `Khối 10`) vẫn lưu được (`src/components/saas/class-form-dialog.tsx:136-138, 233`):** Hiện chỉ hiện `hint` cảnh báo vàng chứ không chặn submit. Nếu muốn chặt theo `System Testing.docx`, chuyển `gradeMismatch` thành `errors.gradeLevel` (hoặc yêu cầu tick xác nhận).
3. **Trang `/staff/admin/payment/history` (`src/components/payment/payment-history.tsx:331-341, 389-404`):** Ô tìm kiếm chỉ lọc trên 20 dòng của trang hiện tại; thẻ tổng doanh thu gọi `/revenue` & `/payments` không truyền `?scope=B2C` trong khi bảng bên dưới chỉ chứa B2C PayOS; mép phải cột thời gian hơi bị cắt ở `1440px`.
4. **Modal gửi báo giá `/for-schools`:** Sau khi gửi thành công, modal xác nhận (`QT-...`) đang mở chồng lên modal form đã xóa trắng thay vì đóng form trước.
5. **Modal `Kết thúc năm học` (`/school/classes`):** Mặc định chọn `Năm học 2025–2026`; nếu đang test/demo năm `2026–2027` dễ bị bấm nhầm nếu không chọn lại. Nên mặc định chọn năm học đang có lớp `ACTIVE` nhiều nhất hoặc năm học hiện tại (`currentAcademicYear()`).
6. **Trang `/pricing` (viewport `1440px`):** Lưới 2 cột đặt `Free` + `Plus` hàng 1 và đẩy `Pro` xuống hàng 2; hiệu ứng scroll-reveal giữ `opacity: 0` nếu cuộn nhảy nhanh qua section.
