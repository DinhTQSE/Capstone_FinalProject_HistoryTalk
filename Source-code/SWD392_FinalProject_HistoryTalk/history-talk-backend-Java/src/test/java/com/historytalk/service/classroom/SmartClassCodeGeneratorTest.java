package com.historytalk.service.classroom;

import com.historytalk.repository.classroom.ClassroomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartClassCodeGeneratorTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @InjectMocks
    private SmartClassCodeGenerator generator;

    @Test
    @DisplayName("Should generate standard semantic class code when no collision occurs")
    void generateClassCode_Standard_NoCollision() {
        when(classroomRepository.existsByClassCodeIgnoreCase("CVA-10A1-2026")).thenReturn(false);

        String classCode = generator.generateClassCode("CVA", "10A1", 2026);

        assertEquals("CVA-10A1-2026", classCode);
        verify(classroomRepository, times(1)).existsByClassCodeIgnoreCase("CVA-10A1-2026");
    }

    @Test
    @DisplayName("Should sanitize Vietnamese diacritics and strip prefix 'Lớp' or 'Class'")
    void generateClassCode_SanitizeVietnameseAndPrefix() {
        when(classroomRepository.existsByClassCodeIgnoreCase("CVA-12CHUYEN-2026")).thenReturn(false);

        String code1 = generator.generateClassCode("CVA", "Lớp 12 Chuyên", 2026);
        assertEquals("CVA-12CHUYEN-2026", code1);

        when(classroomRepository.existsByClassCodeIgnoreCase("LQD-10T1-2025")).thenReturn(false);
        String code2 = generator.generateClassCode("LQD", "Class 10T1", 2025);
        assertEquals("LQD-10T1-2025", code2);
    }

    @Test
    @DisplayName("Should append random 3-char suffix when base code collides")
    void generateClassCode_Collision_AppendsSuffix() {
        when(classroomRepository.existsByClassCodeIgnoreCase("CVA-10A1-2026")).thenReturn(true);
        when(classroomRepository.existsByClassCodeIgnoreCase(argThat(code ->
                code.startsWith("CVA-10A1-2026-") && code.length() == "CVA-10A1-2026-".length() + 3
        ))).thenReturn(false);

        String classCode = generator.generateClassCode("CVA", "10A1", 2026);

        assertNotNull(classCode);
        assertTrue(classCode.startsWith("CVA-10A1-2026-"));
        assertEquals(17, classCode.length()); // "CVA-10A1-2026-" (13) + 3 + 1? let's check: 3+1+4+1+4+1+3 = 17 chars
    }

    @Test
    @DisplayName("Should handle null or special characters gracefully")
    void generateClassCode_HandleNullAndSpecialChars() {
        when(classroomRepository.existsByClassCodeIgnoreCase("SCH-CLS-2026")).thenReturn(false);

        String code = generator.generateClassCode(null, "", null);
        assertEquals("SCH-CLS-2026", code);
    }
}
