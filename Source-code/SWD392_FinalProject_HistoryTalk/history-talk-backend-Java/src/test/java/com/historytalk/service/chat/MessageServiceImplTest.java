package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void getMessagesRejectsSoftDeletedSession() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSession deletedSession = ChatSession.builder()
                .sessionId(sessionId)
                .deletedAt(LocalDateTime.now())
                .build();

        lenient().when(chatSessionRepository.findBySessionIdAndUserUid(sessionId, userId))
                .thenReturn(Optional.of(deletedSession));

        assertThatThrownBy(() -> messageService.getMessages(sessionId.toString(), userId.toString()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Chat session not found");
    }

    @Test
    void sendMessageRejectsSoftDeletedSession() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSession deletedSession = ChatSession.builder()
                .sessionId(sessionId)
                .deletedAt(LocalDateTime.now())
                .build();
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "sessionId", sessionId.toString());
        ReflectionTestUtils.setField(request, "content", "Hello");

        lenient().when(chatSessionRepository.findBySessionIdAndUserUid(sessionId, userId))
                .thenReturn(Optional.of(deletedSession));

        assertThatThrownBy(() -> messageService.sendMessage(userId.toString(), request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Chat session not found");
    }
}
