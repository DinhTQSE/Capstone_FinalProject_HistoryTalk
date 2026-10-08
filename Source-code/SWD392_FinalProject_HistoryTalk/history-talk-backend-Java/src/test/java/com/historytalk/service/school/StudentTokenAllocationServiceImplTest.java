package com.historytalk.service.school;

import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.service.school.token.StudentTokenAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentTokenAllocationServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StudentTokenAllocationServiceImpl allocationService;

    private UUID schoolId;
    private School school;
    private User student1;
    private User student2;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .schoolCode("CVA")
                .name("THPT Chu Văn An")
                .unallocatedTokenQuota(50000)
                .totalSchoolTokenQuota(100000)
                .build();

        student1 = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_01")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(1000)
                .build();

        student2 = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_02")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(500)
                .build();
    }

    @Test
    @DisplayName("Allocate tokens to single student successfully")
    void allocateTokens_SingleStudent_Success() {
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(student1.getUid())
                .tokenAmount(5000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findAllById(any())).thenReturn(List.of(student1));

        AllocateStudentTokenResponse response = allocationService.allocateTokens(schoolId, request);

        assertNotNull(response);
        assertEquals(5000, response.getTotalTokensAllocated());
        assertEquals(1, response.getAffectedStudentCount());
        assertEquals(45000, response.getRemainingSchoolTokens());
        assertEquals(6000, student1.getToken());

        verify(schoolRepository).save(school);
        verify(userRepository).saveAll(any());
    }

    @Test
    @DisplayName("Allocate tokens to multiple students successfully via studentIds list")
    void allocateTokens_MultipleStudents_Success() {
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentIds(List.of(student1.getUid(), student2.getUid()))
                .tokenAmount(2000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findAllById(any())).thenReturn(List.of(student1, student2));

        AllocateStudentTokenResponse response = allocationService.allocateTokens(schoolId, request);

        assertNotNull(response);
        assertEquals(4000, response.getTotalTokensAllocated());
        assertEquals(2, response.getAffectedStudentCount());
        assertEquals(46000, response.getRemainingSchoolTokens());
        assertEquals(3000, student1.getToken());
        assertEquals(2500, student2.getToken());

        verify(schoolRepository).save(school);
        verify(userRepository).saveAll(any());
    }

    @Test
    @DisplayName("Allocate tokens throws InvalidRequestException when neither studentId nor studentIds is provided")
    void allocateTokens_Throws_WhenNoStudentIdsProvided() {
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .tokenAmount(2000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        assertThrows(InvalidRequestException.class, () ->
                allocationService.allocateTokens(schoolId, request));

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Allocate tokens throws InvalidRequestException when quota is insufficient")
    void allocateTokens_Throws_WhenInsufficientQuota() {
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(student1.getUid())
                .tokenAmount(60000) // Exceeds available 50,000
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        assertThrows(InvalidRequestException.class, () ->
                allocationService.allocateTokens(schoolId, request));

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Allocate tokens throws ResourceNotFoundException when student ID not found")
    void allocateTokens_Throws_WhenStudentNotFound() {
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(UUID.randomUUID())
                .tokenAmount(5000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () ->
                allocationService.allocateTokens(schoolId, request));
    }

    @Test
    @DisplayName("Allocate tokens throws InvalidRequestException when user is not a school student")
    void allocateTokens_Throws_WhenUserNotAStudent() {
        User teacher = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_01")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(teacher.getUid())
                .tokenAmount(5000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findAllById(any())).thenReturn(List.of(teacher));

        assertThrows(InvalidRequestException.class, () ->
                allocationService.allocateTokens(schoolId, request));
    }

    @Test
    @DisplayName("Allocate tokens throws InvalidRequestException when student belongs to different school")
    void allocateTokens_Throws_WhenStudentBelongsToDifferentSchool() {
        School otherSchool = School.builder().id(UUID.randomUUID()).schoolCode("LHP").build();
        student1.setSchool(otherSchool);

        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(student1.getUid())
                .tokenAmount(5000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findAllById(any())).thenReturn(List.of(student1));

        assertThrows(InvalidRequestException.class, () ->
                allocationService.allocateTokens(schoolId, request));
    }
}
