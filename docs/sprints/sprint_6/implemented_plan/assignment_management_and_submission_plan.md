# 📋 DETAILED TECHNICAL IMPLEMENTATION PLAN: ASSIGNMENT CREATION, MULTI-TASK SUBMISSION & GRADING ENGINE (SPRINT 6)
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Target User Story:** `docs/sprints/sprint_6/user_stories/class_association_and_assignment_management.md`  
> **Stories Covered:**  
> - `US-SP6-04`: Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline (Assignment Creation & Content Pool Selection)  
> - `US-SP6-05`: Học sinh Nộp bài & Tự động Đánh giá Tiến độ (Assignment Submission & Auto-Grading)  
> - Bugfix/Enhancement: Roster Soft Delete (`INACTIVE` / `TRANSFERRED`) trong `US-SP6-01`  
> **Lập trình viên phụ trách:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Trạng thái:** Kế hoạch Chi tiết Sẵn sàng Triển khai (Ready for Implementation)  

---

## 🎯 1. TỔNG QUAN KIẾN TRÚC & PHẠM VI HỆ THỐNG

Phân hệ Assignment kết nối 3 đối tượng trong hệ thống:
1. **Teacher:** Duyệt kho nội dung (Global/Local) ➔ Xem trước (Preview) ➔ Giao bài cho Lớp (`class_id`) kèm Hạn nộp (`deadline`) ➔ Theo dõi Sổ điểm nộp bài của Lớp.
2. **Student:** Nhận danh sách bài tập của các lớp mình tham gia ➔ Mở bài (`IN_PROGRESS`) ➔ Làm bài (Đọc Bối cảnh / Chat AI / Làm Quiz) ➔ Nộp bài (`SUBMITTED` / `LATE_SUBMITTED`).
3. **System Engine:**
   - **Eager Initialization:** Tự động sinh `assignment_submission` trạng thái `NOT_STARTED` cho toàn bộ học sinh `ACTIVE` trong lớp ngay khi giáo viên bấm giao bài.
   - **Auto-Grading:** Tự động tính điểm % đúng cho Quiz, đếm số tin nhắn đối thoại với nhân vật AI, ghi nhận thời gian đọc bối cảnh.
   - **Resilience & Edge Cases:** Xử lý học sinh mới chuyển lớp (`EXEMPTED`), học sinh rời lớp (bảo toàn lịch sử điểm qua `INACTIVE`), gia hạn deadline (auto-revert `LATE_SUBMITTED` ➔ `SUBMITTED`), và hủy bài tập bằng Soft Delete (`CANCELLED`).

---

## 🗄️ 2. DATABASE SPECIFICATION (FLYWAY MIGRATION V33)

**File path:** `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V33__add_assignment_and_submission_tables.sql`

```sql
-- =========================================================================
-- Flyway V33: Schema for Assignments and Student Submissions (Sprint 6)
-- Author: KhaiVDD
-- =========================================================================

-- 1. Table assignment
CREATE TABLE historical_schema.assignment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id        UUID NOT NULL REFERENCES historical_schema.classroom(id) ON DELETE CASCADE,
    teacher_id          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    assignment_type     VARCHAR(30) NOT NULL, -- 'CONTEXT_READING', 'AI_CHAT_ROLEPLAY', 'QUIZ_TEST'
    target_id           UUID NOT NULL,        -- context_id, character_id, or quiz_id
    deadline            TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'CANCELLED', 'ARCHIVED'
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_assignment_classroom_id ON historical_schema.assignment(classroom_id);
CREATE INDEX idx_assignment_teacher_id ON historical_schema.assignment(teacher_id);
CREATE INDEX idx_assignment_status ON historical_schema.assignment(status);
CREATE INDEX idx_assignment_deadline ON historical_schema.assignment(deadline);

-- 2. Table assignment_submission
CREATE TABLE historical_schema.assignment_submission (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id               UUID NOT NULL REFERENCES historical_schema.assignment(id) ON DELETE CASCADE,
    student_id                  UUID NOT NULL REFERENCES historical_schema."user"(uid),
    status                      VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', 
    -- Statuses: 'NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'LATE_SUBMITTED', 'MISSED', 'EXEMPTED'
    score                       NUMERIC(4, 2) NULL,        -- Thang điểm 10.00 cho bài Quiz
    message_count               INT NOT NULL DEFAULT 0,    -- Số lượt đối thoại với AI
    reading_duration_seconds    INT NOT NULL DEFAULT 0,    -- Số giây đọc bối cảnh
    started_at                  TIMESTAMP WITH TIME ZONE NULL,
    submitted_at                TIMESTAMP WITH TIME ZONE NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_assignment_student UNIQUE (assignment_id, student_id)
);

CREATE INDEX idx_submission_assignment_id ON historical_schema.assignment_submission(assignment_id);
CREATE INDEX idx_submission_student_id ON historical_schema.assignment_submission(student_id);
CREATE INDEX idx_submission_status ON historical_schema.assignment_submission(status);
```

---

## ☕ 3. DATA MODEL & JAVA ENTITY DEFINITIONS

### 3.1 Enums
1. **`AssignmentType`** (`com.historytalk.entity.enums.AssignmentType`):
   ```java
   public enum AssignmentType {
       CONTEXT_READING,   // Đọc Bối cảnh Lịch sử
       AI_CHAT_ROLEPLAY,  // Trò chuyện Nhập vai Nhân vật AI
       QUIZ_TEST          // Làm bài trắc nghiệm tính giờ
   }
   ```
2. **`AssignmentStatus`** (`com.historytalk.entity.enums.AssignmentStatus`):
   ```java
   public enum AssignmentStatus {
       ACTIVE,     // Bài tập đang mở cho học sinh làm
       CANCELLED,  // Giáo viên đã hủy bài (Soft delete)
       ARCHIVED    // Bài tập đã lưu trữ
   }
   ```
3. **`SubmissionStatus`** (`com.historytalk.entity.enums.SubmissionStatus`):
   ```java
   public enum SubmissionStatus {
       NOT_STARTED,    // Mặc định: HS chưa mở bài
       IN_PROGRESS,    // Đang làm: Đã mở bài / đang chat / đang làm quiz
       SUBMITTED,      // Đã nộp đúng hạn (submitted_at <= deadline)
       LATE_SUBMITTED, // Nộp trễ (submitted_at > deadline)
       MISSED,         // Quá hạn mà chưa bấm nộp bài
       EXEMPTED        // Miễn làm (Học sinh chuyển vào sau khi bài đã giao)
   }
   ```

### 3.2 JPA Entity: `Assignment`
**Package:** `com.historytalk.entity.assignment.Assignment`
```java
@Entity
@Table(name = "assignment", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", length = 30, nullable = false)
    private AssignmentType assignmentType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

### 3.3 JPA Entity: `AssignmentSubmission`
**Package:** `com.historytalk.entity.assignment.AssignmentSubmission`
```java
@Entity
@Table(name = "assignment_submission", schema = "historical_schema",
       uniqueConstraints = {@UniqueConstraint(name = "uq_assignment_student", columnNames = {"assignment_id", "student_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentSubmission {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private SubmissionStatus status = SubmissionStatus.NOT_STARTED;

    @Column(name = "score", precision = 4, scale = 2)
    private BigDecimal score;

    @Builder.Default
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Builder.Default
    @Column(name = "reading_duration_seconds", nullable = false)
    private Integer readingDurationSeconds = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

---

## 📡 4. DETAILED API CONTRACTS & JSON PAYLOADS

### 4.1 [Teacher] Giao bài tập mới cho Lớp (POST)
* **Method & Path:** `POST /api/v1/teacher/assignments`
* **Role required:** `TEACHER`
* **Request Headers:** `Authorization: Bearer <jwt_teacher>`
* **Request Body (JSON):**
  ```json
  {
    "classId": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
    "title": "Tìm hiểu Bối cảnh Chiến thắng Bạch Đằng 938",
    "description": "Các em đọc kỹ bối cảnh và chuẩn bị câu hỏi thảo luận vào tiết học thứ 5 tới.",
    "assignmentType": "CONTEXT_READING",
    "targetId": "7c12f4b0-3d89-4a92-b432-11a5b8e90c23",
    "deadline": "2026-10-20T23:59:59"
  }
  ```
* **Validation Rules:**
  - `classId`: Bắt buộc, lớp học phải thuộc `school_id` của giáo viên và giáo viên đang phụ trách lớp.
  - `title`: Bắt buộc, độ dài từ 3 đến 255 ký tự.
  - `assignmentType`: Thuộc `[CONTEXT_READING, AI_CHAT_ROLEPLAY, QUIZ_TEST]`.
  - `targetId`: Phải tồn tại tương ứng trong kho (nếu là Context thì tìm trong `HistoricalContext` hoặc `LocalContext`; nếu là Character thì tìm trong `Character` hoặc `LocalCharacter`; nếu là Quiz thì tìm trong `Quiz`). Nếu là Local Content thì phải có `is_published = true` (status `ACTIVE`).
  - `deadline`: Phải sau thời điểm hiện tại (`deadline.isAfter(LocalDateTime.now())`).
* **Response `201 Created`:**
  ```json
  {
    "success": true,
    "message": "Giao bài tập thành công",
    "data": {
      "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "classroomId": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
      "className": "10A1",
      "title": "Tìm hiểu Bối cảnh Chiến thắng Bạch Đằng 938",
      "description": "Các em đọc kỹ bối cảnh và chuẩn bị câu hỏi thảo luận...",
      "assignmentType": "CONTEXT_READING",
      "targetId": "7c12f4b0-3d89-4a92-b432-11a5b8e90c23",
      "targetTitle": "Trận chiến trên sông Bạch Đằng năm 938",
      "deadline": "2026-10-20T23:59:59",
      "status": "ACTIVE",
      "totalStudentsEnrolled": 38,
      "submissionsInitialized": 38,
      "createdAt": "2026-10-15T17:10:00"
    }
  }
  ```
* **Error Cases:**
  - `400 Bad Request`: Deadline ở quá khứ, hoặc `target_id` không tồn tại / sai type.
  - `403 Forbidden`: Giáo viên không phụ trách lớp này hoặc lớp thuộc trường khác.

---

### 4.2 [Teacher] Xem danh sách bài tập của một Lớp (GET)
* **Method & Path:** `GET /api/v1/teacher/assignments/classroom/{classroomId}`
* **Role required:** `TEACHER`
* **Query Params:**
  - `status` (`String`, optional): Lọc theo `ACTIVE`, `CANCELLED`, `ARCHIVED`.
  - `page` (`int`, default: 0), `size` (`int`, default: 10).
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "message": "Lấy danh sách bài tập thành công",
    "data": {
      "content": [
        {
          "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "title": "Tìm hiểu Bối cảnh Chiến thắng Bạch Đằng 938",
          "assignmentType": "CONTEXT_READING",
          "targetTitle": "Trận chiến trên sông Bạch Đằng năm 938",
          "deadline": "2026-10-20T23:59:59",
          "status": "ACTIVE",
          "totalStudents": 38,
          "submittedCount": 25,
          "lateSubmittedCount": 2,
          "inProgressCount": 5,
          "notStartedCount": 6,
          "createdAt": "2026-10-15T17:10:00"
        }
      ],
      "page": 0,
      "size": 10,
      "totalElements": 1,
      "totalPages": 1
    }
  }
  ```

---

### 4.3 [Teacher] Xem chi tiết Sổ điểm của một Bài tập (GET)
* **Method & Path:** `GET /api/v1/teacher/assignments/{assignmentId}`
* **Role required:** `TEACHER`
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "message": "Chi tiết bài tập và danh sách nộp bài",
    "data": {
      "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "classroomId": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
      "className": "10A1",
      "title": "Kiểm tra 15 phút: Kháng chiến chống quân Nguyên Mông",
      "assignmentType": "QUIZ_TEST",
      "targetTitle": "Bộ câu hỏi ôn tập Ba lần kháng chiến chống Nguyên Mông",
      "deadline": "2026-10-20T23:59:59",
      "status": "ACTIVE",
      "submissions": [
        {
          "submissionId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
          "studentId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
          "studentCode": "HS260001",
          "fullName": "Nguyễn Tuyết Ly",
          "status": "SUBMITTED",
          "score": 9.0,
          "messageCount": 0,
          "readingDurationSeconds": 0,
          "startedAt": "2026-10-16T08:00:00",
          "submittedAt": "2026-10-16T08:14:30"
        },
        {
          "submissionId": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
          "studentId": "e3a1f2aa-7b65-4c12-a1f7-e812859f1962",
          "studentCode": "HS260002",
          "fullName": "Trần Văn Nam",
          "status": "NOT_STARTED",
          "score": null,
          "startedAt": null,
          "submittedAt": null
        }
      ]
    }
  }
  ```

---

### 4.4 [Teacher] Gia hạn Deadline hoặc Cập nhật Bài tập (PUT)
* **Method & Path:** `PUT /api/v1/teacher/assignments/{assignmentId}`
* **Role required:** `TEACHER`
* **Request Body (JSON):**
  ```json
  {
    "title": "Kiểm tra 15 phút: Kháng chiến chống quân Nguyên Mông (Gia hạn)",
    "description": "Cô gia hạn thêm 2 ngày cho các bạn chưa hoàn thành.",
    "deadline": "2026-10-22T23:59:59"
  }
  ```
* **Auto-Revert Logic:** Nếu `new_deadline > old_deadline`, hệ thống tìm tất cả submissions có `status = 'LATE_SUBMITTED'`:
  - Nếu `submitted_at <= new_deadline`: Tự động cập nhật `status = 'SUBMITTED'` (hợp thức hóa đúng hạn).
* **Response `200 OK`:** Trả về bài tập sau khi cập nhật kèm số lượng submissions được chuyển từ `LATE_SUBMITTED` sang `SUBMITTED`.

---

### 4.5 [Teacher] Hủy / Xóa bài tập bằng Soft Delete (DELETE)
* **Method & Path:** `DELETE /api/v1/teacher/assignments/{assignmentId}`
* **Role required:** `TEACHER`
* **Cơ chế Soft Delete:** Cập nhật `assignment.status = 'CANCELLED'` và `assignment.deleted_at = LocalDateTime.now()`.
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "message": "Bài tập đã được hủy thành công (bảo toàn lịch sử bài làm)",
    "data": {
      "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "CANCELLED"
    }
  }
  ```

---

### 4.6 [Student] Xem danh sách bài tập của mình (GET)
* **Method & Path:** `GET /api/v1/student/assignments`
* **Role required:** `SCHOOL_STUDENT`
* **Request Headers:** `Authorization: Bearer <jwt_student>`
* **Query Params:**
  - `status` (`String`, optional): Lọc theo `PENDING` (chưa nộp: `NOT_STARTED` hoặc `IN_PROGRESS`), `COMPLETED` (`SUBMITTED` hoặc `LATE_SUBMITTED`), `MISSED`.
  - `classId` (`UUID`, optional): Lọc theo lớp học.
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "message": "Danh sách bài tập của học sinh",
    "data": [
      {
        "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "classroomId": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
        "className": "10A1",
        "title": "Kiểm tra 15 phút: Kháng chiến chống quân Nguyên Mông",
        "assignmentType": "QUIZ_TEST",
        "targetId": "9b12a345-6789-4abc-def0-123456789abc",
        "targetTitle": "Bộ câu hỏi ôn tập Ba lần kháng chiến chống Nguyên Mông",
        "deadline": "2026-10-20T23:59:59",
        "submissionStatus": "NOT_STARTED",
        "score": null,
        "isExpired": false
      }
    ]
  }
  ```

---

### 4.7 [Student] Bắt đầu làm bài tập (POST start)
* **Method & Path:** `POST /api/v1/student/assignments/{assignmentId}/start`
* **Role required:** `SCHOOL_STUDENT`
* **Logic:**
  - Kiểm tra học sinh thuộc lớp của bài tập (`class_student.status = 'ACTIVE'`).
  - Nếu `submission.status == 'NOT_STARTED'`:
    - Cập nhật `submission.status = 'IN_PROGRESS'`.
    - Ghi nhận `submission.startedAt = LocalDateTime.now()`.
* **Response `200 OK`:** Trả về chi tiết bài tập kèm thông tin target (Bối cảnh/Nhân vật/Quiz) để Frontend điều hướng đến màn hình làm bài tương ứng.

---

### 4.8 [Student] Nộp bài tập & Tự động chấm điểm (POST submit)
* **Method & Path:** `POST /api/v1/student/assignments/{assignmentId}/submit`
* **Role required:** `SCHOOL_STUDENT`
* **Request Body (JSON):**
  ```json
  {
    "score": 8.5,
    "messageCount": 12,
    "readingDurationSeconds": 450
  }
  ```
  *(Frontend gửi payload tương ứng với loại bài: `score` đối với Quiz sau khi kết thúc quiz session; `messageCount` đối với AI Chat; `readingDurationSeconds` đối với Reading Context).*
* **Auto-Grading & Deadline Evaluation:**
  ```java
  LocalDateTime now = LocalDateTime.now();
  submission.setSubmittedAt(now);
  if (now.isAfter(assignment.getDeadline())) {
      submission.setStatus(SubmissionStatus.LATE_SUBMITTED);
  } else {
      submission.setStatus(SubmissionStatus.SUBMITTED);
  }
  if (request.getScore() != null) {
      submission.setScore(request.getScore());
  }
  if (request.getMessageCount() != null) {
      submission.setMessageCount(request.getMessageCount());
  }
  if (request.getReadingDurationSeconds() != null) {
      submission.setReadingDurationSeconds(request.getReadingDurationSeconds());
  }
  ```
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "message": "Nộp bài thành công",
    "data": {
      "submissionId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "assignmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "SUBMITTED",
      "score": 8.5,
      "messageCount": 12,
      "readingDurationSeconds": 450,
      "submittedAt": "2026-10-18T14:20:00"
    }
  }
  ```

---

## 🛠️ 5. CHI TIẾT CÁC STEP THỰC THI (STEP-BY-STEP IMPLEMENTATION)

### 🔹 Bước 1: Tạo DB Migration Flyway V33 & Các Enums
1. Tạo file: `history-talk-backend-Java/src/main/resources/db/migration/V33__add_assignment_and_submission_tables.sql` theo đúng DDL ở Mục 2.
2. Tạo 3 file Enum trong `com.historytalk.entity.enums`:
   - `AssignmentType.java`
   - `AssignmentStatus.java`
   - `SubmissionStatus.java`

### 🔹 Bước 2: Nâng cấp Soft Delete Roster trong `ClassroomStudentServiceImpl.java`
Mở file [ClassroomStudentServiceImpl.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/classroom/ClassroomStudentServiceImpl.java):
1. **Hàm `removeStudentFromClassroom`:**
   - Thay `classStudentRepository.delete(classStudent);` bằng:
     ```java
     classStudent.setStatus("INACTIVE");
     classStudentRepository.save(classStudent);
     ```
2. **Hàm `transferStudents`:**
   - Thay dòng xóa nguồn `classStudentRepository.delete(enrollment);` bằng:
     ```java
     enrollment.setStatus("TRANSFERRED");
     classStudentRepository.save(enrollment);
     ```
   - Khi thêm vào lớp đích: Kiểm tra xem đã có bản ghi nào của học sinh trong lớp đích chưa:
     ```java
     Optional<ClassStudent> existingInTarget = classStudentRepository.findByClassroomIdAndStudentUid(targetClass.getId(), studentId);
     if (existingInTarget.isPresent()) {
         ClassStudent csTarget = existingInTarget.get();
         csTarget.setStatus("ACTIVE");
         classStudentRepository.save(csTarget);
     } else {
         classStudentRepository.save(ClassStudent.builder()
                 .classroom(targetClass)
                 .student(enrollment.getStudent())
                 .status("ACTIVE")
                 .build());
     }
     ```
3. **Hàm `enrollStudentsBatch`:** Tương tự, nếu tìm thấy bản ghi cũ với status `INACTIVE` hoặc `TRANSFERRED`, cập nhật lại `status = "ACTIVE"`.

### 🔹 Bước 3: Tạo JPA Entities & Repositories
1. Tạo package `com.historytalk.entity.assignment`:
   - `Assignment.java`
   - `AssignmentSubmission.java`
2. Tạo package `com.historytalk.repository.assignment`:
   - `AssignmentRepository.java`:
     ```java
     Optional<Assignment> findByIdAndDeletedAtIsNull(UUID id);
     List<Assignment> findByClassroomIdAndStatusAndDeletedAtIsNull(UUID classroomId, AssignmentStatus status);
     Page<Assignment> findByClassroomIdAndDeletedAtIsNull(UUID classroomId, Pageable pageable);
     Page<Assignment> findByClassroomIdAndStatusAndDeletedAtIsNull(UUID classroomId, AssignmentStatus status, Pageable pageable);
     ```
   - `AssignmentSubmissionRepository.java`:
     ```java
     Optional<AssignmentSubmission> findByAssignmentIdAndStudentUid(UUID assignmentId, UUID studentUid);
     List<AssignmentSubmission> findByAssignmentId(UUID assignmentId);
     List<AssignmentSubmission> findByStudentUid(UUID studentUid);
     long countByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);
     long countByAssignmentId(UUID assignmentId);
     ```

### 🔹 Bước 4: Xây dựng Module Giáo viên (`TeacherAssignmentService` & `Controller`)
1. Tạo các DTO trong `com.historytalk.dto.assignment`:
   - `CreateAssignmentRequest.java`
   - `UpdateAssignmentRequest.java`
   - `TeacherAssignmentResponse.java`
   - `AssignmentGradebookResponse.java`
   - `SubmissionItemResponse.java`
2. Tạo Service `TeacherAssignmentService.java` & `TeacherAssignmentServiceImpl.java`:
   - **`createAssignment` logic:**
     1. Lấy `schoolId` & `teacherUid` từ `UserPrincipal`.
     2. Lấy `Classroom`, kiểm tra `classroom.getSchool().getId().equals(schoolId)`.
     3. Kiểm tra tính hợp lệ của `targetId` theo `assignmentType` (gọi tương ứng `ContextService`, `CharacterService`, `QuizService`).
     4. Lưu `Assignment`.
     5. Lấy danh sách toàn bộ học sinh có `status = 'ACTIVE'` trong lớp:
        `List<UUID> activeStudentUids = classStudentRepository.findEnrolledStudentUids(classId);`
     6. Duyệt và tạo danh sách `AssignmentSubmission` với trạng thái `NOT_STARTED`.
     7. Dùng `assignmentSubmissionRepository.saveAll(...)` để insert hàng loạt (Eager Initialization).
3. Tạo Controller `TeacherAssignmentController.java` (`/api/v1/teacher/assignments`) với đầy đủ Swagger OpenAPI annotations.

### 🔹 Bước 5: Xây dựng Module Học sinh (`StudentAssignmentService` & `Controller`)
1. Tạo các DTO:
   - `StudentAssignmentResponse.java`
   - `SubmitAssignmentRequest.java`
   - `StudentSubmissionDetailResponse.java`
2. Tạo Service `StudentAssignmentService.java` & `StudentAssignmentServiceImpl.java`:
   - **`getMyAssignments` logic:**
     1. Lấy `studentUid` từ `UserPrincipal`.
     2. Lấy danh sách các lớp học mà học sinh đang có `status = 'ACTIVE'` (`classStudentRepository.findClassroomsByStudentUid(studentUid)`).
     3. Lấy tất cả bài tập thuộc các lớp này (status != `CANCELLED`).
     4. Map với bản ghi `assignment_submission` của học sinh đó.
   - **`startAssignment` logic:**
     1. Tìm `AssignmentSubmission` theo `(assignmentId, studentUid)`.
     2. Nếu `status == NOT_STARTED`: cập nhật `IN_PROGRESS`, gán `startedAt = now()`.
   - **`submitAssignment` logic:**
     1. Validate submission thuộc về học sinh hiện tại.
     2. So sánh `LocalDateTime.now()` với `assignment.getDeadline()`.
     3. Gán `status = now.isAfter(deadline) ? LATE_SUBMITTED : SUBMITTED`.
     4. Lưu `score` (nếu quiz), `messageCount` (nếu chat), `readingDurationSeconds` (nếu reading).
3. Tạo Controller `StudentAssignmentController.java` (`/api/v1/student/assignments`).

### 🔹 Bước 6: Cấu hình Phân quyền & Compile Check
1. Cập nhật [SecurityConfig.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java):
   ```java
   .requestMatchers("/api/v1/teacher/assignments/**").hasRole("TEACHER")
   .requestMatchers("/api/v1/student/assignments/**").hasRole("SCHOOL_STUDENT")
   ```
2. Chạy lệnh kiểm tra build và biên dịch Java:
   ```powershell
   mvn -q -DskipTests compile
   ```
3. Đảm bảo toàn bộ dự án compile thành công 100%, không phát sinh bất kỳ lỗi cú pháp hay thiếu dependency nào.

---

## 🔍 6. MA TRẬN TEST CASES & TIÊU CHÍ NGHIỆM THU

| Test ID | Tên Kịch bản Kiểm thử | Thao tác / Đầu vào | Kết quả Kỳ vọng |
| :--- | :--- | :--- | :--- |
| **TC-01** | Giao bài tập Quiz mới cho lớp 40 học sinh | `POST /api/v1/teacher/assignments` | Trả về 201 Created; Bảng `assignment_submission` tự động có đúng 40 bản ghi trạng thái `NOT_STARTED`. |
| **TC-02** | Giao bài với Deadline ở quá khứ | `deadline: "2020-01-01T00:00:00"` | Trả về 400 Bad Request: "Hạn nộp bài phải ở tương lai". |
| **TC-03** | Giao bài với Target ID không tồn tại | `targetId: random UUID` | Trả về 404 Not Found: "Nội dung bài tập không tồn tại trong kho". |
| **TC-04** | Giáo viên cố tình giao bài cho lớp của trường khác | `classId: lớp thuộc trường khác` | Trả về 403 Forbidden: "Bạn không có quyền quản lý lớp học này". |
| **TC-05** | Học sinh bấm bắt đầu làm bài | `POST /student/assignments/{id}/start` | Trạng thái chuyển thành `IN_PROGRESS`, `startedAt` được lưu. |
| **TC-06** | Học sinh nộp bài Quiz trước deadline | `POST /student/assignments/{id}/submit` kèm `score: 9.0` | Trạng thái chuyển thành `SUBMITTED`, lưu `submittedAt` và điểm 9.0. |
| **TC-07** | Học sinh nộp bài sau deadline | Nộp khi `now > deadline` | Trạng thái chuyển thành `LATE_SUBMITTED`. |
| **TC-08** | Giáo viên lùi deadline bài tập sang tuần sau | `PUT /teacher/assignments/{id}` với deadline mới | Bài nộp ở TC-07 tự động được hệ thống cập nhật lại thành `SUBMITTED`. |
| **TC-09** | Giáo viên hủy bài tập | `DELETE /teacher/assignments/{id}` | Trạng thái bài tập chuyển thành `CANCELLED`, dữ liệu submission và chat AI vẫn được bảo toàn nguyên vẹn. |
| **TC-10** | Rút học sinh khỏi lớp sau khi đã nộp bài | `DELETE /classrooms/{classId}/students/{studentId}` | Bản ghi `class_student` đổi thành `INACTIVE` (không bị xóa khỏi DB), điểm số cũ vẫn còn nguyên. |
