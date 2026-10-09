-- V31: Add Classroom and Class-Student Association Tables (Sprint 5)

-- 1. Chống trùng Tên trường (School Name Unique Constraint)
CREATE UNIQUE INDEX IF NOT EXISTS idx_school_name_unique 
    ON school (LOWER(name)) 
    WHERE deleted_at IS NULL;

-- 2. Bảng Lớp học (Classroom)
CREATE TABLE IF NOT EXISTS classroom (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    teacher_id UUID REFERENCES "user"(uid) ON DELETE SET NULL,
    class_name VARCHAR(100) NOT NULL,
    class_code VARCHAR(50) NOT NULL UNIQUE,
    grade_level INT,
    academic_year INT NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

-- Ràng buộc chống trùng tên lớp trong cùng một trường và năm học
CREATE UNIQUE INDEX IF NOT EXISTS idx_classroom_school_name_year 
    ON classroom(school_id, LOWER(class_name), academic_year) 
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_classroom_school_id ON classroom(school_id);
CREATE INDEX IF NOT EXISTS idx_classroom_teacher_id ON classroom(teacher_id);
CREATE INDEX IF NOT EXISTS idx_classroom_class_code ON classroom(class_code);

-- 3. Bảng Liên kết Học sinh - Lớp học (Class-Student Association)
CREATE TABLE IF NOT EXISTS class_student (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id UUID NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES "user"(uid) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_class_student UNIQUE (classroom_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_class_student_classroom ON class_student(classroom_id);
CREATE INDEX IF NOT EXISTS idx_class_student_student ON class_student(student_id);
