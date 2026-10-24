package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chat sessions must respect CLASS_ONLY local content: only assigned classes (or the author) may chat. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatSessionClassOnlyIsolationTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ClassLocalContentRepository classLocalContentRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    private School school;
    private User author;
    private Character classOnlyCharacter;
    private UUID assignedClassId;
    private CreateChatSessionRequest request;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("THPT A").build();
        author = User.builder().uid(UUID.randomUUID()).role(UserRole.TEACHER).school(school).build();
        assignedClassId = UUID.randomUUID();
        classOnlyCharacter = Character.builder()
                .characterId(UUID.randomUUID())
                .name("Hai Ba Trung")
                .background("Local character")
                .isPublished(true)
                .school(school)
                .contentScope(ContentScope.CLASS_ONLY)
                .createdBy(author)
                .build();
        request = new CreateChatSessionRequest();
        ReflectionTestUtils.setField(request, "characterId", classOnlyCharacter.getCharacterId().toString());

        when(characterRepository.findById(classOnlyCharacter.getCharacterId())).thenReturn(Optional.of(classOnlyCharacter));
        when(classLocalContentRepository.findClassIdsByEntityIdAndEntityType(
                classOnlyCharacter.getCharacterId(), EntityType.CHARACTER)).thenReturn(List.of(assignedClassId));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            if (s.getSessionId() == null) s.setSessionId(UUID.randomUUID());
            return s;
        });
    }

    private User student() {
        User student = User.builder().uid(UUID.randomUUID()).role(UserRole.SCHOOL_STUDENT).school(school).build();
        when(userRepository.findById(student.getUid())).thenReturn(Optional.of(student));
        return student;
    }

    @Test
    void studentOfAnotherClass_cannotChatWithClassOnlyCharacter() {
        User student = student();
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(Classroom.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> chatSessionService.createSession(student.getUid().toString(), "SCHOOL_STUDENT", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    void studentOfAssignedClass_canChatWithClassOnlyCharacter() {
        User student = student();
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(Classroom.builder().id(assignedClassId).build()));

        var response = chatSessionService.createSession(student.getUid().toString(), "SCHOOL_STUDENT", request);

        assertThat(response.getCharacterId()).isEqualTo(classOnlyCharacter.getCharacterId().toString());
    }

    @Test
    void otherTeacherOfSameSchool_cannotChatWithClassOnlyCharacter() {
        User otherTeacher = User.builder().uid(UUID.randomUUID()).role(UserRole.TEACHER).school(school).build();
        when(userRepository.findById(otherTeacher.getUid())).thenReturn(Optional.of(otherTeacher));

        assertThatThrownBy(() -> chatSessionService.createSession(otherTeacher.getUid().toString(), "TEACHER", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorTeacher_canChatWithOwnClassOnlyCharacter() {
        when(userRepository.findById(author.getUid())).thenReturn(Optional.of(author));

        var response = chatSessionService.createSession(author.getUid().toString(), "TEACHER", request);

        assertThat(response.getCharacterId()).isEqualTo(classOnlyCharacter.getCharacterId().toString());
    }

    @Test
    void schoolWideCharacter_isOpenToWholeSchool() {
        classOnlyCharacter.setContentScope(ContentScope.SCHOOL_WIDE);
        User student = student();

        var response = chatSessionService.createSession(student.getUid().toString(), "SCHOOL_STUDENT", request);

        assertThat(response.getCharacterId()).isEqualTo(classOnlyCharacter.getCharacterId().toString());
        verify(classStudentRepository, never()).findActiveClassroomsByStudentUid(any());
    }
}
