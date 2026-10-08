# 📋 IMPLEMENTATION PLAN: SAAS ROLES & ACCOUNT MANAGEMENT (SPRINT 5)
> **Sprint:** Sprint 5 (04/10/2026 – 10/10/2026)  
> **Target User Story File:** `docs/sprints/sprint_5/user_stories/saas_roles_and_account_management.md`  
> **Stories Covered:** `US-SP5-01`, `US-SP5-02`, `US-SP5-03`, `US-SP5-04`  
> **Lập trình viên phụ trách:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Trạng thái:** Sẵn sàng triển khai (Ready for Execution)

---

## 🎯 1. TỔNG QUAN MỤC TIÊU & PHẠM VI

Kế hoạch này hướng dẫn chi tiết từng bước xây dựng phân hệ **SaaS B2B Multi-tenancy** cho nền tảng HistoryTalk:
1. **`US-SP5-01`**: Quản lý Trường học (`School`) & Thiết lập Hạn mức Token gói Enterprise (`SYSTEM_ADMIN`).
2. **`US-SP5-02`**: Cấp Tài khoản `SCHOOL_ADMIN`, liên kết `school_id`, kích hoạt RBAC Multi-tenancy Isolation.
3. **`US-SP5-03`**: CRUD & Quản lý Trạng thái tài khoản Giáo viên (`TEACHER`) trong phạm vi trường (`SCHOOL_ADMIN`).
4. **`US-SP5-04`**: Nhập hàng loạt tài khoản Học sinh (`SCHOOL_STUDENT`) qua CSV/Excel với cơ chế **Shadow SaaS Email**, kiểm tra **Atomic Token Quota**, xử lý đa hình (Template Method Pattern) và chống trùng mã 100%.

---

## 🏗️ 2. KIẾN TRÚC KỸ THUẬT & QUY CHUẨN BẮT BUỘC

Toàn bộ quá trình code phải tuân thủ nghiêm ngặt các quy chuẩn đã thống nhất:
* **Dependency Injection:** 100% sử dụng **`@RequiredArgsConstructor`** và khai báo `private final` fields. **Tuyệt đối cấm `@Autowired` field injection**.
* **Trách nhiệm tầng:** DTO được build tại **Service Layer** (hoặc Mapper chuyên trách). Controller chỉ đóng vai trò Adapter nhận request, validate cú pháp và bọc kết quả bằng `ApiResponse<T>` hoặc `PaginatedResponse<T>`.
* **Cấu hình tập trung:** Hạn mức token mặc định (10.000), domain email ảo (`@saas.historytalk.vn`), prefix mật khẩu được đưa vào `application.properties` / `secretKey.properties`, không dùng magic numbers.
* **Xử lý Đa hình (Polymorphism):** Áp dụng **Template Method Pattern** (`AbstractStudentImportProcessor`) để chia sẻ khung xương parse file, validate cơ bản, hash mật khẩu và lưu DB.
* **Tương thích B2C:** Áp dụng cơ chế **Shadow SaaS Email** (`{username}@saas.historytalk.vn`) khi học sinh chưa có email, đảm bảo không vi phạm ràng buộc `email NOT NULL` của DB cũ và không làm vỡ các module B2C đang chạy.
* **Kiến trúc Database Multi-Tenancy (Shared DB, Shared Schema):**
  - Dùng chung 1 PostgreSQL database trên Supabase, cô lập logic dữ liệu bằng `school_id`.
  - Giữ 1 Connection Pool duy nhất (HikariCP) để tránh cạn kiệt kết nối trên Supabase Free tier.
  - Tự động trích xuất `school_id` từ JWT Claims (`UserPrincipal`), không tin cậy request body từ client.
  - Đánh Composite Index `(school_id, student_code)` để tối ưu hóa truy vấn.

---

## 🗺️ 3. LỘ TRÌNH TRIỂN KHAI THEO CÁC PHASE (STEP-BY-STEP)

```
Phase 1: Database Migration & Entities
   ├── Flyway V30 (Table school, alter user)
   └── Entity School, Enum EnterprisePackage, update User.java
         │
         ▼
Phase 2: Security & Multi-tenancy Context
   ├── Update UserPrincipal (schoolId, schoolCode)
   └── Config RBAC Controller Security
         │
         ▼
Phase 3: US-SP5-01 & US-SP5-02 (School & School Admin Management)
   ├── DTOs & SchoolRepository
   ├── SchoolManagementService (Tạo trường, cấp quota, tạo School Admin)
   └── AdminSchoolController (/api/v1/admin/schools, /api/v1/admin/school-admins)
         │
         ▼
Phase 4: US-SP5-03 (Teacher Management)
   ├── DTOs & TeacherRepository queries
   ├── TeacherManagementService (CRUD, Active/Inactive)
   └── SchoolAdminTeacherController (/api/v1/school-admin/teachers)
         │
         ▼
Phase 5: US-SP5-04 (Student Bulk Import Engine)
   ├── AbstractStudentImportProcessor & SchoolWideImportProcessor
   ├── Atomic Quota Check & Shadow Email Generator
   └── SchoolAdminStudentController (/api/v1/school-admin/students/import-excel)
         │
         ▼
Phase 6: Verification & Compilation Test
   ├── Maven compile & Unit tests
   └── Swagger UI endpoint verification
```

---

## 📂 4. CHI TIẾT TỪNG PHASE TRIỂN KHAI

### 🔹 PHASE 1: DATABASE MIGRATION & DOMAIN ENTITIES

#### 1.1. Tạo Migration Script Flyway:
* **File:** `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V30__add_school_and_saas_fields.sql`
* **Nội dung:**
  ```sql
  -- 1. Bảng Trường học (School)
  CREATE TABLE IF NOT EXISTS school (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name VARCHAR(255) NOT NULL,
      school_code VARCHAR(50) NOT NULL UNIQUE,
      address VARCHAR(500),
      contact_email VARCHAR(100) NOT NULL,
      contact_phone VARCHAR(20),
      package_type VARCHAR(50) NOT NULL,
      total_school_token_quota INT NOT NULL DEFAULT 0,
      unallocated_token_quota INT NOT NULL DEFAULT 0,
      local_history_policy_accepted BOOLEAN NOT NULL DEFAULT FALSE,
      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP,
      deleted_at TIMESTAMP
  );

  -- 2. Bổ sung trường liên kết SaaS vào bảng "user"
  ALTER TABLE "user" 
      ADD COLUMN IF NOT EXISTS school_id UUID REFERENCES school(id),
      ADD COLUMN IF NOT EXISTS student_code VARCHAR(50),
      ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
      ADD COLUMN IF NOT EXISTS subject_department VARCHAR(100);

  CREATE INDEX IF NOT EXISTS idx_user_school_id ON "user"(school_id);
  CREATE INDEX IF NOT EXISTS idx_user_school_student_code ON "user"(school_id, student_code);
  ```

#### 1.2. Tạo Entity & Enums:
* **File Enum:** `src/main/java/com/historytalk/entity/enums/EnterprisePackage.java`
  - `ENTERPRISE_SMALL(5_000_000)`
  - `ENTERPRISE_MEDIUM(20_000_000)`
  - `ENTERPRISE_LARGE(50_000_000)`
* **File Entity:** `src/main/java/com/historytalk/entity/school/School.java`
  - `@Entity`, `@Table(name = "school")`, `@Getter`, `@Setter`, `@Builder`
  - Khai báo đầy đủ các trường khớp bảng `school`.
* **Cập nhật Entity:** `src/main/java/com/historytalk/entity/user/User.java`
  - Thêm `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "school_id") private School school;`
  - Thêm `studentCode`, `mustChangePassword`, `subjectDepartment`.

---

### 🔹 PHASE 2: BẢO MẬT MULTI-TENANCY CONTEXT

* **File:** `src/main/java/com/historytalk/security/UserPrincipal.java`
  - Bổ sung `private final UUID schoolId;` và `private final String schoolCode;`
  - Khởi tạo `this.schoolId = user.getSchool() != null ? user.getSchool().getId() : null;`
  - Cung cấp getter: `getSchoolId()`, `getSchoolCode()`.

---

### 🔹 PHASE 3: US-SP5-01 & US-SP5-02 (SCHOOL & SCHOOL ADMIN MANAGEMENT)

#### 3.1. DTOs:
* `CreateSchoolRequest`: `name`, `schoolCode`, `address`, `contactEmail`, `contactPhone`, `packageType`, `localHistoryPolicyAccepted`.
* `SchoolResponse`: Trả về thông tin trường, tổng token quota, token chưa phân bổ.
* `CreateSchoolAdminRequest`: `fullName`, `email`, `phoneNumber`, `schoolId`.

#### 3.2. Repository:
* `src/main/java/com/historytalk/repository/school/SchoolRepository.java`:
  - `Optional<School> findBySchoolCodeIgnoreCase(String schoolCode);`
  - `boolean existsBySchoolCodeIgnoreCase(String schoolCode);`

#### 3.3. Service (`SchoolManagementService` & `SchoolManagementServiceImpl`):
* `createSchool(CreateSchoolRequest request)`:
  - Validate trùng lặp `school_code`.
  - Tính toán token theo gói Enterprise: `total_school_token_quota = package.getTokens()`, `unallocated_token_quota = total_school_token_quota`.
  - Validate chấp nhận chính sách `local_history_policy_accepted == true`.
  - Lưu bản ghi và trả về `SchoolResponse`.
* `createSchoolAdmin(CreateSchoolAdminRequest request)`:
  - Tìm trường theo `schoolId`.
  - Sinh username: `{school_code}_admin`.
  - Sinh mật khẩu ngẫu nhiên an toàn, mã hóa BCrypt.
  - Tạo User với role `SCHOOL_ADMIN`, gán `school`, `mustChangePassword = true`.
  - Gửi email kích hoạt / thông báo mật khẩu khởi tạo cho School Admin.

#### 3.4. Controller:
* `src/main/java/com/historytalk/controller/school/AdminSchoolController.java`:
  - `@RestController`, `@RequestMapping("/api/v1/admin")`, `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`
  - `POST /schools`: Gọi service tạo trường, trả về `ApiResponse.success(resp, "Tạo trường học thành công")`.
  - `POST /school-admins`: Gọi service tạo School Admin, trả về `ApiResponse.success(resp, "Cấp tài khoản School Admin thành công")`.

---

### 🔹 PHASE 4: US-SP5-03 (TEACHER MANAGEMENT)

#### 4.1. DTOs:
* `CreateTeacherRequest`: `fullName`, `email`, `phoneNumber`, `subjectDepartment`.
* `TeacherResponse`: Thông tin giáo viên, tổ bộ môn, trạng thái tài khoản.
* `UpdateTeacherStatusRequest`: `status` (`ACTIVE` / `INACTIVE`).

#### 4.2. Service (`TeacherManagementService` & `TeacherManagementServiceImpl`):
* `createTeacher(CreateTeacherRequest request, UUID schoolId)`:
  - Sinh username: `{school_code}_gv_{random/seq}`.
  - Tạo user role `TEACHER`, gán `schoolId`, `subjectDepartment`.
  - Sinh mật khẩu tạm, hash BCrypt.
* `getTeachers(UUID schoolId, String search, Pageable pageable)`:
  - Phân trang giáo viên trong cùng `schoolId`, trả về `PaginatedResponse<TeacherResponse>`.
* `updateTeacherStatus(UUID teacherId, UUID schoolId, boolean active)`:
  - Kiểm tra giáo viên thuộc đúng trường của School Admin thao tác.
  - Cập nhật cờ `deletedAt` (soft-delete) hoặc trạng thái hoạt động.

#### 4.3. Controller:
* `src/main/java/com/historytalk/controller/school/SchoolAdminTeacherController.java`:
  - `@RestController`, `@RequestMapping("/api/v1/school-admin/teachers")`, `@PreAuthorize("hasRole('SCHOOL_ADMIN')")`
  - Các endpoints: `POST /`, `GET /`, `PUT /{id}/status`.

---

### 🔹 PHASE 5: US-SP5-04 (STUDENT BULK IMPORT ENGINE)

#### 5.1. DTOs:
* `StudentImportRowDto`: Chứa thông tin 1 dòng (`studentCode`, `fullName`, `email`, `dob`, `gender`, `phoneNumber`, `classCode`, `password`, `additionalToken`).
* `StudentImportResultDto`: Thống kê tổng số dòng, số dòng thành công, số dòng lỗi, danh sách lỗi chi tiết từng dòng, và danh sách tài khoản được tạo kèm plaintext password ban đầu.

#### 5.2. Architecture Implementation:
1. **Interface:** `src/main/java/com/historytalk/service/school/student/StudentImportProcessor.java`:
   ```java
   public interface StudentImportProcessor {
       StudentImportResultDto processImport(MultipartFile file, StudentImportContext context);
   }
   ```
2. **Abstract Class (Template Method):** `AbstractStudentImportProcessor.java`:
   - `@RequiredArgsConstructor` với `protected final` repositories & passwordEncoder.
   - Quản lý quy trình chuẩn:
     1. `parseRecords(file)` (hỗ trợ cả `.csv` UTF-8 BOM và `.xlsx` Apache POI).
     2. `validateCommonRules(rows)` (họ tên, ngày sinh ISO, studentCode regex).
     3. Abstract `resolveClassrooms(rows, context)`.
     4. Abstract `verifyAndDeductQuota(rows, context)`.
     5. `persistAccounts(rows, context)`:
        - Sinh Shadow SaaS Email `{username}@saas.historytalk.vn` nếu dòng để trống email.
        - Hash BCrypt mật khẩu.
     6. `buildResultReport(accounts)`.
3. **Concrete Class:** `SchoolWideImportProcessor.java`:
   - Triển khai cho School Admin:
     - Đọc `class_code` từ từng dòng trong file.
     - Tính tổng Token: $\sum (\text{default\_token} + \text{additional\_token})$.
     - Kiểm tra với `school.getUnallocatedTokenQuota()`, ném `InsufficientQuotaException` nếu vượt quá.
     - Trừ trực tiếp vào `unallocated_token_quota`.

#### 5.3. Controller:
* `src/main/java/com/historytalk/controller/school/SchoolAdminStudentController.java`:
  - Endpoint: `POST /api/v1/school-admin/students/import-excel` (hỗ trợ multipart file `.csv` / `.xlsx`).
  - Lấy `schoolId` từ `UserPrincipal`, gọi `SchoolWideImportProcessor`.
  - Trả về `ApiResponse.success(resultDto, "Import danh sách học sinh hoàn tất")`.

---

## 🧪 5. QUY TRÌNH KIỂM THỬ & VALIDATION

1. **Biên dịch mã nguồn:**
   ```powershell
   mvn -q -DskipTests compile
   ```
2. **Chạy Unit Test:**
   ```powershell
   mvn test -Dtest=SchoolManagementServiceTest,StudentImportProcessorTest
   ```
3. **Kiểm tra API trên Swagger UI:**
   * Truy cập: `http://localhost:8080/Historical-tell/api/v1/swagger-ui`
   * Kiểm tra đầy đủ 6 API endpoints mới:
     - `POST /api/v1/admin/schools`
     - `POST /api/v1/admin/school-admins`
     - `POST /api/v1/school-admin/teachers`
     - `GET /api/v1/school-admin/teachers`
     - `PUT /api/v1/school-admin/teachers/{id}/status`
     - `POST /api/v1/school-admin/students/import-excel`
4. **Test file Import:**
   * Test với 2 file mẫu vừa tạo:
     - [student_import_school_template.csv](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/student_import_school_template.csv)
     - [student_import_classroom_template.csv](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/student_import_classroom_template.csv)
