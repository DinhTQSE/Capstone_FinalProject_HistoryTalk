package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.BatchEnrollResultResponse;
import com.historytalk.dto.classroom.BatchEnrollStudentRequest;
import com.historytalk.dto.classroom.ClassroomStudentResponse;
import com.historytalk.dto.classroom.TransferStudentRequest;
import com.historytalk.dto.school.StudentResponse;
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
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomStudentServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentImportService studentImportService;

    private ClassroomStudentServiceImpl classroomStudentService;

    private School school;
    private School otherSchool;
    private User adminUser;
    private User teacherUser;
    private User otherTeacherUser;
    private User student1;
    private User student2;
    private User studentOtherSchool;
    private Classroom classroomA;
    private Classroom classroomB;
    private UserPrincipal adminPrincipal;
    private UserPrincipal teacherPrincipal;
    private UserPrincipal otherTeacherPrincipal;

    @BeforeEach
    void setUp() {
        classroomStudentService = new ClassroomStudentServiceImpl(
                classroomRepository,
                classStudentRepository,
                userRepository,
                studentImportService
        );

        UUID schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .name("THPT Chu Văn An")
                .schoolCode("CVA")
                .unallocatedTokenQuota(50000)
                .build();

        otherSchool = School.builder()
                .id(UUID.randomUUID())
                .name("THPT Lê Hồng Phong")
                .schoolCode("LHP")
                .build();

        adminUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_admin")
                .email("admin@cva.edu.vn")
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        teacherUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_teacher")
                .email("teacher@cva.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        otherTeacherUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_teacher2")
                .email("teacher2@cva.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        student1 = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_01")
                .studentCode("HS001")
                .fullName("Nguyễn Văn An")
                .email("an@cva.edu.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(10000)
                .build();

        student2 = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_02")
                .studentCode("HS002")
                .fullName("Trần Thị Bình")
                .email("binh@cva.edu.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(10000)
                .build();

        studentOtherSchool = User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_hs_01")
                .studentCode("LHP001")
                .fullName("Lê Văn Cường")
                .email("cuong@lhp.edu.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .school(otherSchool)
                .build();

        classroomA = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .teacher(teacherUser)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .academicYear(2026)
                .status("ACTIVE")
                .build();

        classroomB = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .teacher(teacherUser)
                .className("10A2")
                .classCode("CVA-10A2-2026")
                .academicYear(2026)
                .status("ACTIVE")
                .build();

        adminPrincipal = new UserPrincipal(adminUser);
        teacherPrincipal = new UserPrincipal(teacherUser);
        otherTeacherPrincipal = new UserPrincipal(otherTeacherUser);
    }

    @Test
    @DisplayName("Batch Enroll: Thành công gán 2 học sinh vào lớp")
    void testEnrollStudentsBatch_Success() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(userRepository.findById(student1.getUid())).thenReturn(Optional.of(student1));
        when(userRepository.findById(student2.getUid())).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassroomIdAndStudentUid(classroomA.getId(), student1.getUid())).thenReturn(false);
        when(classStudentRepository.existsByClassroomIdAndStudentUid(classroomA.getId(), student2.getUid())).thenReturn(false);
        when(classStudentRepository.countByClassroomId(classroomA.getId())).thenReturn(2L);

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(student1.getUid(), student2.getUid()))
                .build();

        BatchEnrollResultResponse result = classroomStudentService.enrollStudentsBatch(classroomA.getId(), req, adminPrincipal);

        assertEquals(2, result.getTotalRequested());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getAlreadyEnrolledCount());
        assertEquals(2L, result.getCurrentStudentCount());
        verify(classStudentRepository, times(2)).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Batch Enroll: Bỏ qua an toàn (Idempotent) học sinh đã có trong lớp")
    void testEnrollStudentsBatch_AlreadyEnrolled() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(userRepository.findById(student1.getUid())).thenReturn(Optional.of(student1));
        when(classStudentRepository.existsByClassroomIdAndStudentUid(classroomA.getId(), student1.getUid())).thenReturn(true);
        when(classStudentRepository.countByClassroomId(classroomA.getId())).thenReturn(1L);

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(student1.getUid()))
                .build();

        BatchEnrollResultResponse result = classroomStudentService.enrollStudentsBatch(classroomA.getId(), req, teacherPrincipal);

        assertEquals(1, result.getTotalRequested());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getAlreadyEnrolledCount());
        verify(classStudentRepository, never()).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Batch Enroll: Từ chối học sinh thuộc trường khác (Tenant check)")
    void testEnrollStudentsBatch_CrossSchoolRejected() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(userRepository.findById(studentOtherSchool.getUid())).thenReturn(Optional.of(studentOtherSchool));
        when(classStudentRepository.countByClassroomId(classroomA.getId())).thenReturn(0L);

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(studentOtherSchool.getUid()))
                .build();

        BatchEnrollResultResponse result = classroomStudentService.enrollStudentsBatch(classroomA.getId(), req, adminPrincipal);

        assertEquals(1, result.getTotalRequested());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getNotes().get(0).contains("không thuộc trường"));
        verify(classStudentRepository, never()).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Batch Enroll: Giáo viên không phụ trách lớp sẽ bị chặn AccessDeniedException")
    void testEnrollStudentsBatch_TeacherNotAssigned_ThrowsAccessDenied() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(student1.getUid()))
                .build();

        assertThrows(AccessDeniedException.class, () ->
                classroomStudentService.enrollStudentsBatch(classroomA.getId(), req, otherTeacherPrincipal));
    }

    @Test
    @DisplayName("Remove Student: Gỡ học sinh khỏi lớp thành công")
    void testRemoveStudentFromClass_Success() {
        ClassStudent enrollment = ClassStudent.builder()
                .id(UUID.randomUUID())
                .classroom(classroomA)
                .student(student1)
                .status("ACTIVE")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(classStudentRepository.findByClassroomIdAndStudentUid(classroomA.getId(), student1.getUid()))
                .thenReturn(Optional.of(enrollment));

        assertDoesNotThrow(() ->
                classroomStudentService.removeStudentFromClass(classroomA.getId(), student1.getUid(), adminPrincipal));

        verify(classStudentRepository, times(1)).delete(enrollment);
    }

    @Test
    @DisplayName("Remove Student: Học sinh không có trong lớp ném ResourceNotFoundException")
    void testRemoveStudentFromClass_NotFound() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(classStudentRepository.findByClassroomIdAndStudentUid(classroomA.getId(), student1.getUid()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                classroomStudentService.removeStudentFromClass(classroomA.getId(), student1.getUid(), teacherPrincipal));
    }

    @Test
    @DisplayName("Transfer Student: Chuyển học sinh từ lớp A sang lớp B thành công")
    void testTransferStudents_Success() {
        ClassStudent enrollmentA = ClassStudent.builder()
                .id(UUID.randomUUID())
                .classroom(classroomA)
                .student(student1)
                .status("ACTIVE")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomB.getId(), school.getId()))
                .thenReturn(Optional.of(classroomB));
        when(classStudentRepository.findByClassroomIdAndStudentUid(classroomA.getId(), student1.getUid()))
                .thenReturn(Optional.of(enrollmentA));
        when(classStudentRepository.existsByClassroomIdAndStudentUid(classroomB.getId(), student1.getUid()))
                .thenReturn(false);
        when(classStudentRepository.countByClassroomId(classroomB.getId())).thenReturn(1L);

        TransferStudentRequest req = TransferStudentRequest.builder()
                .targetClassroomId(classroomB.getId())
                .studentIds(List.of(student1.getUid()))
                .build();

        BatchEnrollResultResponse result = classroomStudentService.transferStudents(classroomA.getId(), req, adminPrincipal);

        assertEquals(1, result.getSuccessCount());
        verify(classStudentRepository, times(1)).delete(enrollmentA);
        verify(classStudentRepository, times(1)).save(any(ClassStudent.class));
    }

    @Test
    @DisplayName("Transfer Student: Chuyển vào chính lớp hiện tại ném InvalidRequestException")
    void testTransferStudents_SameClass_ThrowsInvalidRequest() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));

        TransferStudentRequest req = TransferStudentRequest.builder()
                .targetClassroomId(classroomA.getId())
                .studentIds(List.of(student1.getUid()))
                .build();

        assertThrows(InvalidRequestException.class, () ->
                classroomStudentService.transferStudents(classroomA.getId(), req, adminPrincipal));
    }

    @Test
    @DisplayName("Get Classroom Students: Lấy danh sách học sinh theo lớp phân trang")
    void testGetClassroomStudents_Success() {
        ClassStudent cs1 = ClassStudent.builder()
                .id(UUID.randomUUID())
                .classroom(classroomA)
                .student(student1)
                .joinedAt(LocalDateTime.now())
                .status("ACTIVE")
                .build();

        Page<ClassStudent> page = new PageImpl<>(List.of(cs1), PageRequest.of(0, 10), 1);

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(classStudentRepository.searchStudentsInClassroom(eq(classroomA.getId()), eq(school.getId()), eq("An"), any(Pageable.class)))
                .thenReturn(page);

        PaginatedResponse<ClassroomStudentResponse> result = classroomStudentService.getClassroomStudents(
                classroomA.getId(), "An", PageRequest.of(0, 10), teacherPrincipal);

        assertEquals(1, result.getTotalElements());
        assertEquals("HS001", result.getContent().get(0).getStudentCode());
        assertEquals("Nguyễn Văn An", result.getContent().get(0).getFullName());
    }

    @Test
    @DisplayName("Get Available Students: Lấy danh sách học sinh trường chưa vào lớp")
    void testGetAvailableStudents_Success() {
        Page<User> page = new PageImpl<>(List.of(student2), PageRequest.of(0, 10), 1);

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomA.getId(), school.getId()))
                .thenReturn(Optional.of(classroomA));
        when(userRepository.findAvailableStudentsForClassroom(eq(school.getId()), eq(classroomA.getId()), any(Pageable.class)))
                .thenReturn(page);

        PaginatedResponse<StudentResponse> result = classroomStudentService.getAvailableStudents(
                classroomA.getId(), null, PageRequest.of(0, 10), adminPrincipal);

        assertEquals(1, result.getTotalElements());
        assertEquals("HS002", result.getContent().get(0).getStudentCode());
    }
}
