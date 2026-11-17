package com.historytalk.service.chat;

import com.historytalk.dto.chat.ChatHistoryGroupResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChatHistoryServiceImpl chatHistoryService;

    @Test
    @DisplayName("WF-03 / F-04: getHistory returns empty list when user has no chat sessions")
    void getHistory_EmptySessions_ReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(chatSessionRepository.findAllByUserUid(userId, false)).thenReturn(List.of());

        List<ChatHistoryGroupResponse> result = chatHistoryService.getHistory(userId.toString());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("WF-03 / F-04: getHistory groups sessions by character and sorts by latest message timestamp")
    void getHistory_WithSessions_GroupsByCharacterAndSortsDescending() {
        UUID userId = UUID.randomUUID();
        UUID charId = UUID.randomUUID();
        UUID ctxId = UUID.randomUUID();
        UUID session1Id = UUID.randomUUID();
        UUID session2Id = UUID.randomUUID();

        Character character = Character.builder()
                .characterId(charId)
                .name("Trần Hưng Đạo")
                .title("Hưng Đạo Đại Vương")
                .imageUrl("https://cdn.historytalk.vn/thd.png")
                .build();

        HistoricalContext context = HistoricalContext.builder()
                .contextId(ctxId)
                .name("Chiến thắng Bạch Đằng 1288")
                .build();

        ChatSession s1 = ChatSession.builder()
                .sessionId(session1Id)
                .character(character)
                .historicalContext(context)
                .title("Bàn kế sách giữ nước")
                .mode(ChatSessionMode.CHAT)
                .lastMessageAt(LocalDateTime.now().minusHours(2))
                .build();

        ChatSession s2 = ChatSession.builder()
                .sessionId(session2Id)
                .character(character)
                .historicalContext(context)
                .title("Vấn đáp Bạch Đằng")
                .mode(ChatSessionMode.EXAM)
                .lastMessageAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(chatSessionRepository.findAllByUserUid(userId, false)).thenReturn(List.of(s1, s2));
        when(messageRepository.countMessagesBySessionIds(anyList()))
                .thenReturn(List.of(
                        new Object[]{session1Id, 4L},
                        new Object[]{session2Id, 6L}
                ));
        when(messageRepository.findLatestMessageContentBySessionIds(anyList()))
                .thenReturn(List.of(
                        new Object[]{session1Id, "Khoan thư sức dân để làm kế sâu rễ bền gốc"},
                        new Object[]{session2Id, "Ngươi trả lời rất tốt!"}
                ));

        List<ChatHistoryGroupResponse> groups = chatHistoryService.getHistory(userId.toString());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getContextName()).isEqualTo("Trần Hưng Đạo");
        assertThat(groups.get(0).getSessions()).hasSize(2);
        assertThat(groups.get(0).getSessions().get(0).getId()).isEqualTo(session2Id.toString());
        assertThat(groups.get(0).getSessions().get(0).getMessageCount()).isEqualTo(6);
        assertThat(groups.get(0).getSessions().get(0).getMode()).isEqualTo("EXAM");
    }
}
