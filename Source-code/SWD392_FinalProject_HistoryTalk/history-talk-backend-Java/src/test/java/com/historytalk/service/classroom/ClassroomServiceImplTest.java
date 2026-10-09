package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.ClassroomResponse;
import com.historytalk.dto.classroom.CreateClassroomRequest;
import com.historytalk.dto.classroom.UpdateClassroomRequest;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SmartClassCodeGenerator smartClassCodeGenerator;

    @InjectMocks
    private ClassroomServiceImpl classroomService;

    private UUID schoolId;
    private School school;
    private User teacher;
    private User studentUser;
    private UserPrincipal adminPrincipal;
    private UserPrincipal teacherPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .schoolCode("CVA")
                .name("THPT Chu Văn An")
                .build();

        teacher = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_01")
                .fullName("Nguyễn Thị Mai")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        User adminUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_admin")
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        studentUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_01")
                .fullName("Trần Quốc Dinh")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .build();

        adminPrincipal = new UserPrincipal(adminUser);
        teacherPrincipal = new UserPrincipal(teacher);
        studentPrincipal = new UserPrincipal(studentUser);
    }

    @Test
    @DisplayName("Create classroom successfully by School Admin")
    void createClassroom_Success() {
        CreateClassroomRequest request = CreateClassroomRequest.builder()
                .className("10A1")
                .gradeLevel(10)
                .academicYear(2026)
                .teacherId(teacher.getUid())
                .description("Lớp chuyên sử")
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
                schoolId, "10A1", 2026)).thenReturn(false);
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(smartClassCodeGenerator.generateClassCode("CVA", "10A1", 2026)).thenReturn("CVA-10A1-2026");

        Classroom savedClassroom = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .teacher(teacher)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .gradeLevel(10)
                .academicYear(2026)
                .status("ACTIVE")
                .build();

        when(classroomRepository.save(any(Classroom.class))).thenReturn(savedClassroom);

        ClassroomResponse response = classroomService.createClassroom(request, adminPrincipal);

        assertNotNull(response);
        assertEquals("10A1", response.getClassName());
        assertEquals("CVA-10A1-2026", response.getClassCode());
        assertNotNull(response.getTeacher());
        assertEquals("Nguyễn Thị Mai", response.getTeacher().getFullName());

        verify(classroomRepository).save(any(Classroom.class));
    }

    @Test
    @DisplayName("Create classroom throws DataConflictException when class name duplicates in academic year")
    void createClassroom_Throws_WhenDuplicateNameInAcademicYear() {
        CreateClassroomRequest request = CreateClassroomRequest.builder()
                .className("10A1")
                .gradeLevel(10)
                .academicYear(2026)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
                schoolId, "10A1", 2026)).thenReturn(true);

        assertThrows(DataConflictException.class, () ->
                classroomService.createClassroom(request, adminPrincipal));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create classroom throws InvalidRequestException when teacher belongs to different school")
    void createClassroom_Throws_WhenTeacherBelongsToDifferentSchool() {
        School otherSchool = School.builder().id(UUID.randomUUID()).schoolCode("LQD").build();
        User otherTeacher = User.builder().uid(UUID.randomUUID()).role(UserRole.TEACHER).school(otherSchool).build();

        CreateClassroomRequest request = CreateClassroomRequest.builder()
                .className("10A1")
                .gradeLevel(10)
                .academicYear(2026)
                .teacherId(otherTeacher.getUid())
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
                schoolId, "10A1", 2026)).thenReturn(false);
        when(userRepository.findById(otherTeacher.getUid())).thenReturn(Optional.of(otherTeacher));

        assertThrows(InvalidRequestException.class, () ->
                classroomService.createClassroom(request, adminPrincipal));
    }

    @Test
    @DisplayName("Update classroom successfully by School Admin")
    void updateClassroom_Success() {
        UUID classroomId = UUID.randomUUID();
        Classroom classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .teacher(teacher)
                .className("10A1")
                .academicYear(2026)
                .gradeLevel(10)
                .status("ACTIVE")
                .build();

        UpdateClassroomRequest request = UpdateClassroomRequest.builder()
                .className("10A1 Chuyên")
                .description("Cập nhật mô tả")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));
        when(classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndIdNotAndDeletedAtIsNull(
                schoolId, "10A1 Chuyên", 2026, classroomId)).thenReturn(false);
        when(classroomRepository.save(any(Classroom.class))).thenReturn(classroom);
        when(classStudentRepository.countByClassroomId(classroomId)).thenReturn(35L);

        ClassroomResponse response = classroomService.updateClassroom(classroomId, request, adminPrincipal);

        assertNotNull(response);
        assertEquals("10A1 Chuyên", response.getClassName());
        assertEquals(35L, response.getStudentCount());
    }

    @Test
    @DisplayName("Teacher updating another teacher's classroom throws AccessDeniedException")
    void updateClassroom_Throws_TeacherNotOwner() {
        UUID classroomId = UUID.randomUUID();
        User otherTeacher = User.builder().uid(UUID.randomUUID()).school(school).build();
        Classroom classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .teacher(otherTeacher)
                .className("10A1")
                .academicYear(2026)
                .build();

        UpdateClassroomRequest request = UpdateClassroomRequest.builder()
                .className("10A2")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));

        assertThrows(AccessDeniedException.class, () ->
                classroomService.updateClassroom(classroomId, request, teacherPrincipal));
    }

    @Test
    @DisplayName("Delete classroom sets soft-delete timestamp")
    void deleteClassroom_Success() {
        UUID classroomId = UUID.randomUUID();
        Classroom classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .className("10A1")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));

        classroomService.deleteClassroom(classroomId, adminPrincipal);

        assertNotNull(classroom.getDeletedAt());
        verify(classroomRepository).save(classroom);
    }

    @Test
    @DisplayName("Get classroom by ID returns details with student count")
    void getClassroomById_Success() {
        UUID classroomId = UUID.randomUUID();
        Classroom classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .teacher(teacher)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));
        when(classStudentRepository.countByClassroomId(classroomId)).thenReturn(40L);

        ClassroomResponse response = classroomService.getClassroomById(classroomId, adminPrincipal);

        assertNotNull(response);
        assertEquals("10A1", response.getClassName());
        assertEquals(40L, response.getStudentCount());
    }

    @Test
    @DisplayName("Get school classrooms paginated")
    void getSchoolClassrooms_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Classroom classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .className("10A1")
                .build();
        Page<Classroom> page = new PageImpl<>(List.of(classroom), pageable, 1);

        when(classroomRepository.findSchoolClassrooms(eq(schoolId), eq("10A1"), eq(10), eq(2026), eq(pageable)))
                .thenReturn(page);
        when(classStudentRepository.countByClassroomId(any())).thenReturn(20L);

        PaginatedResponse<ClassroomResponse> result = classroomService.getSchoolClassrooms(
                "10A1", 10, 2026, pageable, adminPrincipal);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        assertEquals(20L, result.getContent().get(0).getStudentCount());
    }

    @Test
    @DisplayName("Get student classrooms returns list of classrooms student is enrolled in")
    void getStudentClassrooms_Success() {
        Classroom classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .className("10A1")
                .build();

        when(classStudentRepository.findClassroomsByStudentUid(studentUser.getUid()))
                .thenReturn(List.of(classroom));
        when(classStudentRepository.countByClassroomId(any())).thenReturn(30L);

        List<ClassroomResponse> list = classroomService.getStudentClassrooms(studentPrincipal);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("10A1", list.get(0).getClassName());
    }
}
