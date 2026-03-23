package com.historyTalk.service.chat;

import com.historyTalk.dto.chat.ChatHistoryGroupResponse;
import com.historyTalk.dto.chat.ChatHistorySessionItem;
import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.chat.Message;
import com.historyTalk.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatSessionRepository chatSessionRepository;

    @Transactional(readOnly = true)
    public List<ChatHistoryGroupResponse> getHistory(String userId) {
        log.info("Getting chat history for user={}", userId);

        List<ChatSession> sessions = chatSessionRepository.findAllByUserUid(UUID.fromString(userId), false);

        // Group by contextId
        Map<String, List<ChatSession>> grouped = sessions.stream()
                .filter(s -> s.getHistoricalContext() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getHistoricalContext().getContextId().toString()));

        // Build response groups, sorted by max lastMessageAt DESC
        return grouped.entrySet().stream()
                .map(entry -> {
                    String contextId = entry.getKey();
                    List<ChatSession> groupSessions = entry.getValue();

                    // Sort sessions within group by lastMessageAt DESC (null last)
                    List<ChatHistorySessionItem> items = groupSessions.stream()
                            .sorted(Comparator.comparing(
                                    ChatSession::getLastMessageAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .map(this::mapToSessionItem)
                            .toList();

                    String contextName = groupSessions.get(0).getHistoricalContext() != null 
                            ? groupSessions.get(0).getHistoricalContext().getName() 
                            : "[Deleted Context]";

                    return ChatHistoryGroupResponse.builder()
                            .contextId(contextId)
                            .contextName(contextName)
                            .sessions(items)
                            .build();
                })
                // Sort groups by latest lastMessageAt DESC
                .sorted(Comparator.comparing(
                        g -> g.getSessions().stream()
                                .map(ChatHistorySessionItem::getLastMessageAt)
                                .filter(t -> t != null)
                                .max(Comparator.naturalOrder())
                                .orElse(LocalDateTime.MIN),
                        Comparator.reverseOrder()))
                .toList();
    }

    private ChatHistorySessionItem mapToSessionItem(ChatSession session) {
        List<Message> messages = session.getMessages();

        String lastMessage = messages.stream()
                .max(Comparator.comparing(Message::getTimestamp))
                .map(Message::getContent)
                .orElse(null);

        return ChatHistorySessionItem.builder()
                .id(session.getSessionId().toString())
                .characterId(safeGetCharacterId(session))
                .characterName(safeGetCharacterName(session))
                .characterTitle(safeGetCharacterTitle(session))
                .characterImage(safeGetCharacterImage(session))
                .contextId(safeGetContextId(session))
                .contextName(safeGetContextName(session))
                .sessionTitle(session.getTitle())
                .lastMessage(lastMessage)
                .lastMessageAt(session.getLastMessageAt())
                .messageCount(messages.size())
                .build();
    }

    private String safeGetCharacterId(ChatSession session) {
        try {
            var c = session.getCharacter();
            return c != null ? c.getCharacterId().toString() : null;
        } catch (Exception e) { return null; }
    }

    private String safeGetCharacterName(ChatSession session) {
        try {
            var c = session.getCharacter();
            return c != null ? c.getName() : "[Deleted Character]";
        } catch (Exception e) { return "[Deleted Character]"; }
    }

    private String safeGetCharacterTitle(ChatSession session) {
        try {
            var c = session.getCharacter();
            return c != null ? c.getTitle() : null;
        } catch (Exception e) { return null; }
    }

    private String safeGetCharacterImage(ChatSession session) {
        try {
            var c = session.getCharacter();
            return c != null ? c.getImage() : null;
        } catch (Exception e) { return null; }
    }

    private String safeGetContextId(ChatSession session) {
        try {
            var ctx = session.getHistoricalContext();
            return ctx != null ? ctx.getContextId().toString() : null;
        } catch (Exception e) { return null; }
    }

    private String safeGetContextName(ChatSession session) {
        try {
            var ctx = session.getHistoricalContext();
            return ctx != null ? ctx.getName() : "[Deleted Context]";
        } catch (Exception e) { return "[Deleted Context]"; }
    }
}
