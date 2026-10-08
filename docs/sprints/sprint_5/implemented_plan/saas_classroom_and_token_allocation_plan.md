# 📋 IMPLEMENTATION PLAN: SAAS CLASSROOM MANAGEMENT & TOKEN ALLOCATION (SPRINT 5)
> **Sprint:** Sprint 5 (04/10/2026 – 10/10/2026)  
> **Target User Story File:** `docs/sprints/sprint_5/user_stories/saas_classroom_management.md`  
> **Stories Covered:** `US-SP5-05`, `US-SP5-06`, `US-SP5-07`  
> **Lập trình viên phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)  
> **Trạng thái:** Bắt đầu triển khai (In Progress)  

---

## 🎯 1. TỔNG QUAN MỤC TIÊU & PHẠM VI

Kế hoạch này triển khai toàn bộ các tính năng thuộc trách nhiệm của **Trần Quốc Dinh** trong Sprint 5:
1. **`US-SP5-05`**: Cơ chế School Admin phân bổ hạn mức Token cho Học sinh (`PUT /api/v1/school-admin/students/token-allocation`) từ quỹ `unallocated_token_quota` của trường (hỗ trợ phân bổ đơn lẻ hoặc phân bổ đều hàng loạt).
2. **`US-SP5-06`**: CRUD Lớp học toàn trường (`Classroom`) với thuật toán **Smart Class Code Generator** (`{school_code}-{clean_class_name}-{academic_year}`), xử lý va chạm mã và chống trùng tên lớp trong cùng trường/năm học.
3. **`US-SP5-07`**: Các API truy xuất danh sách Lớp học theo phân quyền Role (`School Admin`, `Teacher`, `School Student`) và API xem chi tiết lớp học.
4. **Phụ trợ**:
   - Bổ sung Unique Index chống trùng Tên trường (`name`) theo Phương án 1 đã thống nhất.
   - Tạo sẵn bảng liên kết Học sinh - Lớp học (`class_student`) để sẵn sàng cho Sprint 6.
   - Thêm API tiện ích tải file CSV template mẫu cho School Admin (`GET /api/v1/school-admin/students/template`).

---

## 🏗️ 2. KIẾN TRÚC KỸ THUẬT & QUY CHUẨN

* **Kiến trúc phân tầng:** Controller $\rightarrow$ Service $\rightarrow$ Repository $\rightarrow$ Database.
* **Dependency Injection:** Sử dụng `@RequiredArgsConstructor` với `private final` fields. Không dùng `@Autowired`.
* **Multi-tenancy Isolation:** Lấy `schoolId` trực tiếp từ `UserPrincipal` (JWT Claims). Chặn 100% truy cập chéo trường.
* **Ownership Check (Kiểm tra quyền sở hữu):**
  - `SCHOOL_ADMIN`: Toàn quyền với mọi lớp trong trường.
  - `TEACHER`: Chỉ quản lý/sửa/xem danh sách lớp do chính mình phụ trách (`classroom.teacher_id == currentUser.uid`).
  - `SCHOOL_STUDENT`: Chỉ xem các lớp mình tham gia trong bảng `class_student`.
* **Transaction Management:** `@Transactional` đảm bảo tính nguyên tử khi trừ quota trường và cộng token cho học sinh.

---

## 🗺️ 3. LỘ TRÌNH THỰC HIỆN THEO CÁC PHASE

```
Phase 1: Database Migration (Flyway V31)
   └── V31__add_classroom_and_class_student_tables.sql
         │
         ▼
Phase 2: Domain Entities & Repositories
   ├── Classroom.java & ClassStudent.java
   ├── Cập nhật School.java & SchoolRepository (check trùng name)
   └── ClassroomRepository.java & ClassStudentRepository.java
         │
         ▼
Phase 3: US-SP5-05 (Student Token Allocation Engine)
   ├── DTOs (AllocateStudentTokenRequest, AllocateStudentTokenResponse)
   ├── StudentTokenAllocationService & Implementation
   └── Endpoint PUT /api/v1/school-admin/students/token-allocation
         │
         ▼
Phase 4: US-SP5-06 & US-SP5-07 (Classroom Management & Smart Class Code)
   ├── SmartClassCodeGenerator (Thuật toán sinh mã lớp ngữ nghĩa)
   ├── DTOs (CreateClassroomRequest, UpdateClassroomRequest, ClassroomResponse, TeacherSummaryDto)
   ├── ClassroomService & Implementation
   └── ClassroomController (CRUD, Get by Roles)
         │
         ▼
Phase 5: Download CSV Template API
   └── GET /api/v1/school-admin/students/template
         │
         ▼
Phase 6: Testing & Verification
   ├── mvn -q -DskipTests compile
   ├── Unit tests
   └── Swagger UI endpoint check
```

---

## 📂 4. DANH SÁCH FILE CẦN TẠO VÀ CHỈNH SỬA

| STT | File Path | Hành động | Mục đích |
| :--- | :--- | :--- | :--- |
| 1 | `src/main/resources/db/migration/V31__add_classroom_and_class_student_tables.sql` | Tạo mới | Migration tạo bảng `classroom`, `class_student`, unique index school name |
| 2 | `src/main/java/com/historytalk/entity/classroom/Classroom.java` | Tạo mới | JPA Entity cho Lớp học |
| 3 | `src/main/java/com/historytalk/entity/classroom/ClassStudent.java` | Tạo mới | JPA Entity liên kết Học sinh - Lớp học |
| 4 | `src/main/java/com/historytalk/repository/school/SchoolRepository.java` | Chỉnh sửa | Bổ sung `existsByNameIgnoreCase(String name)` |
| 5 | `src/main/java/com/historytalk/service/school/SchoolManagementServiceImpl.java` | Chỉnh sửa | Thêm validate chống trùng Tên trường |
| 6 | `src/main/java/com/historytalk/repository/classroom/ClassroomRepository.java` | Tạo mới | Repository truy vấn lớp học theo tenant, teacher, code |
| 7 | `src/main/java/com/historytalk/repository/classroom/ClassStudentRepository.java` | Tạo mới | Repository quản lý liên kết học sinh - lớp |
| 8 | `src/main/java/com/historytalk/dto/school/AllocateStudentTokenRequest.java` | Tạo mới | Request DTO phân bổ token học sinh |
| 9 | `src/main/java/com/historytalk/dto/school/AllocateStudentTokenResponse.java` | Tạo mới | Response DTO kết quả phân bổ token |
| 10 | `src/main/java/com/historytalk/service/school/token/StudentTokenAllocationService.java` | Tạo mới | Interface phân bổ token học sinh |
| 11 | `src/main/java/com/historytalk/service/school/token/StudentTokenAllocationServiceImpl.java` | Tạo mới | Implementation phân bổ token |
| 12 | `src/main/java/com/historytalk/service/classroom/SmartClassCodeGenerator.java` | Tạo mới | Component sinh mã lớp thông minh |
| 13 | `src/main/java/com/historytalk/dto/classroom/CreateClassroomRequest.java` | Tạo mới | Request tạo lớp học |
| 14 | `src/main/java/com/historytalk/dto/classroom/UpdateClassroomRequest.java` | Tạo mới | Request cập nhật lớp học |
| 15 | `src/main/java/com/historytalk/dto/classroom/TeacherSummaryDto.java` | Tạo mới | DTO tóm tắt giáo viên phụ trách |
| 16 | `src/main/java/com/historytalk/dto/classroom/ClassroomResponse.java` | Tạo mới | Response chi tiết lớp học |
| 17 | `src/main/java/com/historytalk/service/classroom/ClassroomService.java` | Tạo mới | Interface nghiệp vụ lớp học |
| 18 | `src/main/java/com/historytalk/service/classroom/ClassroomServiceImpl.java` | Tạo mới | Implementation nghiệp vụ lớp học |
| 19 | `src/main/java/com/historytalk/controller/classroom/ClassroomController.java` | Tạo mới | REST Controller quản lý lớp học |
| 20 | `src/main/java/com/historytalk/controller/school/SchoolAdminStudentController.java` | Chỉnh sửa | Bổ sung endpoint phân bổ token & download template |
