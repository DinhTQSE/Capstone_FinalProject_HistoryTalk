# Kế hoạch Triển khai: Phân tích Chi tiết Tiêu thụ Token theo Lớp và Phân tách Token Giáo viên vs Học sinh

> **Mục tiêu:** Giải quyết trọn vẹn các yêu cầu và băn khoăn của team về việc Quản trị trường học (School Admin) bị "mù thông tin" khi kho token bị tiêu hao hết mà không biết do lớp nào hay học sinh nào sử dụng; đồng thời tách biệt minh bạch giữa lượng token Giáo viên sử dụng và Học sinh sử dụng.

---

## 1. Bối cảnh & Phân tích Đề xuất từ Nhóm

Dựa trên trao đổi thảo luận của nhóm (Nguyễn Công Thành, Minh Trí Nguyễn và Trần Quốc Dinh):
1. **School Admin cần xem chi tiết tiêu thụ token theo từng lớp và từng học sinh:**
   - *Hiện trạng:* School Admin Dashboard chỉ hiển thị tổng token đã dùng của toàn trường (`tokensUsedByStudents`), không biết lớp nào chiếm dụng nhiều, học sinh nào spam chat gây cạn kiệt kho token chung.
   - *Đề xuất:* Thêm bảng thống kê tiêu thụ token theo từng lớp, xếp hạng lớp dùng nhiều nhất và hỗ trợ drill-down vào từng học sinh trong lớp.
2. **Phải tách biệt Token Giáo viên (GV) sử dụng vs Token Học sinh (HS) sử dụng:**
   - *Hiện trạng:* Cả GV và HS đều trừ chung vào kho `school.unallocatedTokenQuota`, và hàm thống kê `sumTokensUsedBySchoolStudents` đang cộng gộp tất cả người dùng thuộc trường.
   - *Đề xuất:* Tách thành 2 chỉ số riêng biệt: `tokensUsedByStudents` và `tokensUsedByTeachers`, cùng tỷ lệ % đóng góp của từng nhóm.
3. **Thống kê theo lớp vs theo trường (Giáo viên dạy nhiều lớp):**
   - *Vấn đề đặt ra:* Giáo viên dạy 2, 3 lớp thì thống kê riêng từng lớp hay gộp chung?
   - *Giải pháp thiết kế:*
     - **Ở cấp Trường (School Admin):** Xem toàn bộ danh sách các lớp trong trường, so sánh mức độ tiêu hao token giữa các lớp.
     - **Ở cấp Giáo viên phụ trách:** Mỗi lớp có dashboard riêng biệt (`/teacher/dashboard/classrooms/{classId}`). Bổ sung thêm API tổng quan các lớp GV phụ trách (`/teacher/dashboard/classes-overview`) để so sánh giữa các lớp mình giảng dạy.
     - **Token của chính GV:** Được ghi nhận độc lập dưới danh mục "Token Giáo viên", không tính trùng vào bất kỳ lớp học sinh nào.

---

## 2. Thiết kế Kiến trúc & Dữ liệu

### 2.1. Phân tách Token theo Vai trò (Role-based Token Aggregation)
- Trong [`MessageRepository.java`](file:///c:/Users/trand/OneDrive/Documents/Semester%207/SWD392/SWD392_FinalProject_Git/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/MessageRepository.java):
  - Cập nhật `sumTokensUsedBySchoolStudents`: thêm điều kiện `u.role = 'SCHOOL_STUDENT'`.
  - Bổ sung `sumTokensUsedBySchoolTeachers`: điều kiện `u.role = 'TEACHER'`.
  - Bổ sung hàm tính tổng token theo ngày hôm nay:
    - `sumTokensUsedBySchoolStudentsToday(schoolId, startOfToday)`
    - `sumTokensUsedBySchoolTeachersToday(schoolId, startOfToday)`

### 2.2. Thống kê Tiêu thụ Token theo Lớp (Classroom Token Consumption Projection)
- Tạo Spring Data JPA Projection hoặc DTO: `ClassroomTokenUsageProjection`:
  ```java
  public interface ClassroomTokenUsageProjection {
      UUID getClassroomId();
      String getClassName();
      String getClassCode();
      Integer getGradeLevel();
      Integer getAcademicYear();
      String getStatus();
      String getTeacherName();
      Long getStudentCount();
      Long getTokensUsedTotal();
      Long getTokensUsedToday();
  }
  ```
- Native Query tối ưu:
  - Join `classroom` $\rightarrow$ `class_student` (active) $\rightarrow$ `chat_session` $\rightarrow$ `message`.
  - Gom nhóm theo `classroom_id`, tính `SUM(m.token)` tổng thể và `SUM(m.token)` trong ngày hôm nay.

### 2.3. Drill-down Thống kê Từng Học sinh trong Lớp (Student Token Breakdown Projection)
- Tạo Projection/DTO: `StudentTokenUsageDetailDto`:
  - `studentId`, `studentCode`, `fullName`, `userName`, `email`
  - `tokensUsedTotal` (tổng số token đã tiêu thụ từ đầu năm)
  - `tokensUsedToday` (số token đã tiêu thụ hôm nay)
  - `dailyTokenLimit` (hạn mức ngày cơ bản)
  - `dailyBonusTokens` (token được GV/Admin cấp thêm trong ngày)
  - `effectiveRemainingToday` (hạn mức còn lại trong ngày)
  - `messageCount` (tổng số tin nhắn chat AI)
  - `lastActiveDate` (thời điểm gần nhất học sinh chat AI)

---

## 3. Danh sách Endpoint API Mới & Cập nhật

### 3.1. Cập nhật `SchoolAdminDashboardResponse`
- **Endpoint:** `GET /api/v1/school-admin/dashboard`
- **Các trường bổ sung:**
  ```json
  {
    "tokensUsedByStudents": 185000,
    "tokensUsedByTeachers": 25000,
    "tokensUsedTotal": 210000,
    "studentTokenUsagePercentage": 88.1,
    "teacherTokenUsagePercentage": 11.9,
    "topClassroomsByTokenUsage": [
      {
        "classroomId": "...",
        "className": "10A1",
        "classCode": "CVA-10A1-2026",
        "gradeLevel": 10,
        "teacherName": "Thầy Nguyễn Văn A",
        "studentCount": 35,
        "tokensUsedTotal": 65000,
        "tokensUsedToday": 3200,
        "percentageOfSchoolUsage": 30.95
      }
    ]
  }
  ```

### 3.2. API Mới: Xem Chi tiết Tiêu thụ Token Toàn bộ các Lớp (Phân trang & Lọc)
- **Endpoint:** `GET /api/v1/school-admin/analytics/classrooms`
- **Role:** `SCHOOL_ADMIN`, `ADMIN`, `SYSTEM_ADMIN`
- **Query Params:**
  - `academicYear` (Integer, tùy chọn)
  - `gradeLevel` (Integer, tùy chọn)
  - `status` (String, mặc định `ACTIVE`)
  - `search` (String: tìm theo tên lớp, mã lớp, tên giáo viên)
  - `page`, `size`, `sort` (ví dụ: `sort=tokensUsedTotal,desc` hoặc `sort=tokensUsedToday,desc`)
- **Response:** `PaginatedApiResponse<ClassroomTokenUsageDto>`

### 3.3. API Mới: Drill-down Chi tiết Học sinh trong Lớp
- **Endpoint:** `GET /api/v1/school-admin/analytics/classrooms/{classroomId}/students`
- **Role:** `SCHOOL_ADMIN`, `TEACHER` (giáo viên phụ trách lớp), `ADMIN`, `SYSTEM_ADMIN`
- **Query Params:**
  - `search` (String: tìm theo tên học sinh, mã HS)
  - `page`, `size`, `sort` (sắp xếp theo `tokensUsedTotal,desc` để đưa học sinh dùng nhiều nhất lên đầu)
- **Response:** `PaginatedApiResponse<StudentTokenUsageDetailDto>`

### 3.4. API Mới: Tổng quan Các Lớp Dành Cho Giáo viên
- **Endpoint:** `GET /api/v1/teacher/dashboard/classes-overview`
- **Role:** `TEACHER`
- **Ý nghĩa:** Giải quyết câu hỏi *"Giáo viên dạy 2, 3 lớp thì thống kê ra sao"*. API này trả về danh sách toàn bộ các lớp mà giáo viên đó đang phụ trách, kèm số lượng học sinh và lượng token tiêu thụ của từng lớp để so sánh trực quan.

---

## 4. Kế hoạch Các Bước Triển khai (Step-by-Step Implementation)

| Bước | Hạng mục công việc | File ảnh hưởng chính |
| :---: | :--- | :--- |
| **B1** | Bổ sung Native Query và Projection trong Repository | `MessageRepository.java`, `ClassroomRepository.java`, các Projection interface mới trong `repository/dashboard/` |
| **B2** | Cập nhật DTO Response và API Models | `SchoolAdminDashboardResponse.java`, tạo `ClassroomTokenUsageDto.java`, `StudentTokenUsageDetailDto.java`, `TeacherClassesOverviewResponse.java` |
| **B3** | Triển khai Business Logic trong Service Layer | `AnalyticsDashboardService.java`, `AnalyticsDashboardServiceImpl.java` |
| **B4** | Cung cấp REST Endpoints trong Controller Layer | `AnalyticsDashboardController.java` |
| **B5** | Viết Unit Test & Integration Test kiểm thử toàn diện | `AnalyticsDashboardServiceImplTest.java`, `AnalyticsDashboardControllerTest.java` |
| **B6** | Cập nhật Tài liệu Hợp đồng Frontend & Sprints | `docs/sprints/sprint_6/fe_contracts/dashboards_and_analytics_api_contract.md`, `docs/sprints/sprint_7/` |

---

## 5. Kế hoạch Kiểm thử & Xác thực (Verification)
1. **Kiểm thử phân tách Token GV vs HS:**
   - Tạo tin nhắn chat từ tài khoản `TEACHER` $\rightarrow$ xác nhận `tokensUsedByTeachers` tăng, `tokensUsedByStudents` không đổi.
   - Tạo tin nhắn chat từ tài khoản `SCHOOL_STUDENT` $\rightarrow$ xác nhận `tokensUsedByStudents` tăng, `tokensUsedByTeachers` không đổi.
2. **Kiểm thử thống kê theo Lớp:**
   - Học sinh lớp `10A1` chat $\rightarrow$ kiểm tra query thống kê của lớp `10A1` tăng chính xác số token tương ứng, lớp `10A2` không bị ảnh hưởng.
3. **Kiểm thử Giáo viên dạy nhiều lớp:**
   - Giáo viên A dạy cả `10A1` và `10A2`: gọi `/teacher/dashboard/classes-overview` kiểm tra danh sách gồm đúng 2 lớp với số liệu phân tách riêng biệt.
4. **Kiểm thử hiệu năng (Query Performance):**
   - Đảm bảo các chỉ mục (indexes) trên `chat_session(uid)`, `message(session_id, created_at, deleted_at)`, `class_student(classroom_id, student_id, status)` hoạt động tối ưu khi trường có hàng nghìn học sinh.
