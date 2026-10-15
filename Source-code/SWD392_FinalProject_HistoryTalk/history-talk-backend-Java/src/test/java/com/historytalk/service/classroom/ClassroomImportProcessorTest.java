package com.historytalk.service.classroom;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomImportProcessorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    private ClassroomImportProcessor processor;

    private School school;
    private Classroom classroom;
    private UUID schoolId;
    private UUID classroomId;

    @BeforeEach
    void setUp() {
        processor = new ClassroomImportProcessor(
                userRepository,
                schoolRepository,
                passwordEncoder,
                classroomRepository,
                classStudentRepository
        );

        schoolId = UUID.randomUUID();
        classroomId = UUID.randomUUID();

        school = School.builder()
                .id(schoolId)
                .schoolCode("CVA")
                .name("THPT Chu Văn An")
                .unallocatedTokenQuota(50000)
                .build();

        classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .academicYear(2026)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Import CSV: Học sinh đã tồn tại trong trường -> Gán vào lớp, không trừ token trường")
    void testProcessImport_ExistingStudent_EnrollsWithoutTokenDeduction() {
        String csv = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS001,Trần Bảo Nam,,2009-05-15,MALE,0912345601,,0\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        User existingStudent = User.builder()
                .uid(UUID.randomUUID())
                .studentCode("HS001")
                .userName("cva_hs_hs001")
                .fullName("Trần Bảo Nam")
                .email("nam@cva.edu.vn")
                .school(school)
                .role(UserRole.SCHOOL_STUDENT)
                .token(15000)
                .build();

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .defaultInitialToken(10000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)).thenReturn(Optional.of(classroom));
        when(userRepository.findBySchoolIdAndStudentCodeIgnoreCase(schoolId, "HS001")).thenReturn(Optional.of(existingStudent));
        when(classStudentRepository.findByClassroomIdAndStudentUid(classroomId, existingStudent.getUid())).thenReturn(Optional.empty());

        StudentImportResultDto result = processor.processImport(file, context);

        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(50000, result.getRemainingSchoolTokens()); // Không bị trừ token
        assertEquals("(Tài khoản hiện có)", result.getSuccessfulAccounts().get(0).getInitialPassword());
        assertEquals(0, result.getSuccessfulAccounts().get(0).getAllocatedToken());

        verify(classStudentRepository, times(1)).save(any(ClassStudent.class));
        verify(schoolRepository, never()).save(any(School.class)); // Không trừ token trường
    }

    @Test
    @DisplayName("Import CSV: Học sinh đã có sẵn trong lớp -> Bỏ qua an toàn (Idempotent), không trùng lặp")
    void testProcessImport_AlreadyEnrolled_Idempotent() {
        String csv = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS001,Trần Bảo Nam,,2009-05-15,MALE,0912345601,,0\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        User existingStudent = User.builder()
                .uid(UUID.randomUUID())
                .studentCode("HS001")
                .userName("cva_hs_hs001")
                .fullName("Trần Bảo Nam")
                .email("nam@cva.edu.vn")
                .school(school)
                .role(UserRole.SCHOOL_STUDENT)
                .build();

        ClassStudent existingEnrollment = ClassStudent.builder()
                .classroom(classroom)
                .student(existingStudent)
                .status("ACTIVE")
                .build();

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .defaultInitialToken(10000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)).thenReturn(Optional.of(classroom));
        when(userRepository.findBySchoolIdAndStudentCodeIgnoreCase(schoolId, "HS001")).thenReturn(Optional.of(existingStudent));
        when(classStudentRepository.findByClassroomIdAndStudentUid(classroomId, existingStudent.getUid())).thenReturn(Optional.of(existingEnrollment));

        StudentImportResultDto result = processor.processImport(file, context);

        assertEquals(1, result.getSuccessCount());
        assertEquals("(Đã có trong lớp từ trước)", result.getSuccessfulAccounts().get(0).getInitialPassword());
        verify(classStudentRepository, never()).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Import CSV: Học sinh mới tinh -> Tạo tài khoản, trừ token trường và gán vào lớp")
    void testProcessImport_NewStudent_CreatesAndEnrollsAndDeductsTokens() {
        String csv = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS002,Lê Thảo Vy,,2009-08-20,FEMALE,0987654321,,5000\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .defaultInitialToken(10000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)).thenReturn(Optional.of(classroom));
        when(userRepository.findBySchoolIdAndStudentCodeIgnoreCase(schoolId, "HS002")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(userRepository.countBySchoolIdAndRole(schoolId, UserRole.SCHOOL_STUDENT)).thenReturn(10L);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setUid(UUID.randomUUID());
            return u;
        });

        StudentImportResultDto result = processor.processImport(file, context);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        // Initial token = 10000 + 5000 additional = 15000. Remaining: 50000 - 15000 = 35000
        assertEquals(35000, school.getUnallocatedTokenQuota());
        assertEquals(35000, result.getRemainingSchoolTokens());
        assertEquals(15000, result.getSuccessfulAccounts().get(0).getAllocatedToken());
        assertEquals("HS002@2026", result.getSuccessfulAccounts().get(0).getInitialPassword());

        verify(schoolRepository, times(1)).save(school);
        verify(userRepository, times(1)).save(any(User.class));
        verify(classStudentRepository, times(1)).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Import CSV: Không đủ quota token cho học sinh mới -> Ném InvalidRequestException")
    void testProcessImport_InsufficientTokens_ThrowsInvalidRequest() {
        school.setUnallocatedTokenQuota(5000); // Only 5000 available

        String csv = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS002,Lê Thảo Vy,,2009-08-20,FEMALE,0987654321,,10000\n"; // Requires 10000 (base) + 10000 (extra) = 20000
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .defaultInitialToken(10000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)).thenReturn(Optional.of(classroom));
        when(userRepository.findBySchoolIdAndStudentCodeIgnoreCase(schoolId, "HS002")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> processor.processImport(file, context));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Import CSV: File rỗng -> Ném InvalidRequestException")
    void testProcessImport_EmptyFile_ThrowsInvalidRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .build();

        assertThrows(InvalidRequestException.class, () -> processor.processImport(emptyFile, context));
    }

    @Test
    @DisplayName("Import CSV: Thiếu classroomId trong context -> Ném InvalidRequestException")
    void testProcessImport_MissingClassroomId_ThrowsInvalidRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "some,content".getBytes());
        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(null)
                .build();

        assertThrows(InvalidRequestException.class, () -> processor.processImport(file, context));
    }

    @Test
    @DisplayName("Import CSV: Lớp học không tồn tại -> Ném ResourceNotFoundException")
    void testProcessImport_ClassroomNotFound_ThrowsResourceNotFound() {
        String csv = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS001,Trần Bảo Nam,,2009-05-15,MALE,0912345601,,0\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .classroomId(classroomId)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> processor.processImport(file, context));
    }
}
