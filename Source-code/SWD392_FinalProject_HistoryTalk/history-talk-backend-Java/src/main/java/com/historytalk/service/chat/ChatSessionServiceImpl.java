package com.historytalk.service.chat;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.chat.ChatScenarioResponse;
import com.historytalk.dto.chat.ChatSessionResponse;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;

import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.service.character.CharacterPronouns;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.service.chat.AiServiceClient.AiChatResult;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int DEFAULT_EXAM_QUESTION_COUNT = 5;
    private static final int FREE_B2C_EXAM_QUESTION_LIMIT = 2;

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final HistoricalContextRepository contextRepository;
    private final MessageRepository messageRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final ClassLocalContentRepository classLocalContentRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ExamSessionService examSessionService;
    private final TransactionTemplate transactionTemplate;
    private final UserTierRepository userTierRepository;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(String userId, String characterId) {
        log.info("Getting chat sessions for user={} character={}", userId, characterId);

        List<ChatSession> sessions = chatSessionRepository.findByUserAndCharacter(
                UUID.fromString(userId),
                UUID.fromString(characterId));
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream().map(ChatSession::getSessionId).toList();
        java.util.Map<UUID, Integer> countMap = new java.util.HashMap<>();
        java.util.Map<UUID, String> lastMsgMap = new java.util.HashMap<>();
        for (Object[] row : messageRepository.countMessagesBySessionIds(sessionIds)) {
            countMap.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        for (Object[] row : messageRepository.findLatestMessageContentBySessionIds(sessionIds)) {
            lastMsgMap.putIfAbsent((UUID) row[0], (String) row[1]);
        }

        return sessions.stream()
                .map(s -> mapToResponse(
                        s,
                        lastMsgMap.get(s.getSessionId()),
                        countMap.getOrDefault(s.getSessionId(), 0)))
                .toList();
    }

    // The greeting comes from the LLM, which takes seconds while the connection pool is small,
    // so the session is saved and the greeting is saved in separate short transactions.
    private record PreparedSession(
            ChatSession session,
            String characterId,
            String characterName,
            String selfPronoun,
            String addressPronoun,
            String contextId,
            CharacterPayload characterData,
            ContextPayload contextData) {
    }

    public ChatSessionResponse createSession(String userId, String userRole, CreateChatSessionRequest request) {
        PreparedSession prepared = transactionTemplate.execute(status -> prepareSession(userId, userRole, request));
        ChatSession saved = prepared.session();

        // An exam opens with its first question instead of a greeting
        if (saved.getMode() == ChatSessionMode.EXAM) {
            try {
                Message opening = examSessionService.startExam(saved.getSessionId());
                saved.setLastMessageAt(opening.getCreatedAt());
                return mapToResponse(saved, opening.getContent(), 1);
            } catch (RuntimeException e) {
                transactionTemplate.executeWithoutResult(status -> chatSessionRepository.deleteById(saved.getSessionId()));
                throw e;
            }
        }

        // Send greeting message via AI
        String greetingText = null;
        List<String> suggestedQuestionsList = null;

        try {
            AiChatResult greeting = aiServiceClient.chat(
                    prepared.characterId(),
                    prepared.contextId(),
                    "Hãy chào và giới thiệu ngắn gọn về bản thân.",
                    Collections.emptyList(),
                    prepared.characterData(),
                    prepared.contextData(),
                    true);

            if (greeting != null && greeting.message() != null && !greeting.message().isBlank()) {
                greetingText = greeting.message();
                suggestedQuestionsList = greeting.suggestedQuestions();
            }
        } catch (Exception e) {
            log.warn("Failed to generate greeting for session {}: {}", saved.getSessionId(), e.getMessage());
        }

        // Fallback greeting if AI fails or returns empty response
        if (greetingText == null || greetingText.isBlank()) {
            greetingText = "Xin chào, " + prepared.selfPronoun() + " là " + prepared.characterName() + ". "
                    + CharacterPronouns.capitalize(prepared.addressPronoun()) + " muốn biết điều gì?";
        }

        if (suggestedQuestionsList == null || suggestedQuestionsList.isEmpty()) {
            suggestedQuestionsList = List.of(
                    "Ngài có thể kể cho ta nghe về cuộc đời của ngài không?",
                    "Chiến công hoặc sự kiện đáng nhớ nhất của ngài là gì?",
                    "Ngài có thể chia sẻ thêm về bối cảnh lịch sử thời đó không?");
        }

        String suggestedQuestionsJson = null;
        try {
            suggestedQuestionsJson = objectMapper.writeValueAsString(suggestedQuestionsList);
        } catch (Exception ex) {
            log.warn("Failed to serialize suggested questions: {}", ex.getMessage());
        }

        Message greetingMsg = Message.builder()
                .content(greetingText)
                .isFromAi(true)
                .suggestedQuestions(suggestedQuestionsJson)
                .chatSession(saved)
                .build();

        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            messageRepository.save(greetingMsg);
            chatSessionRepository.findById(saved.getSessionId()).ifPresent(session -> session.setLastMessageAt(now));
        });
        saved.setLastMessageAt(now);

        return mapToResponse(saved, greetingText, 1);
    }

    private PreparedSession prepareSession(String userId, String userRole, CreateChatSessionRequest request) {
        log.info("Creating chat session for user={} context={} character={}", userId, request.getContextId(),
                request.getCharacterId());

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == com.historytalk.entity.enums.UserRole.CUSTOMER && (user.getToken() == null || user.getToken() <= 0)) {
            throw new InvalidRequestException("Bạn đã hết token. Vui lòng nạp thêm để tiếp tục chat.");
        }

        Character character = characterRepository.findById(UUID.fromString(request.getCharacterId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân vật với ID: " + request.getCharacterId()));

        if (!isStaffOrAdmin(userRole) && !isPubliclyVisible(character.getIsPublished(), character.getDeletedAt())) {
            throw new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + request.getCharacterId());
        }

        // School Isolation: Local school character is only accessible to users of the same school
        if (character.getSchool() != null && !"SYSTEM_ADMIN".equalsIgnoreCase(userRole)) {
            if (user.getSchool() == null || !user.getSchool().getId().equals(character.getSchool().getId())) {
                throw new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + request.getCharacterId());
            }
            if (!canAccessClassOnly(user, userRole, character.getContentScope(), character.getCreatedBy(),
                    character.getCharacterId(), EntityType.CHARACTER)) {
                throw new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + request.getCharacterId());
            }
        }

        HistoricalContext context = null;
        if (request.getContextId() != null && !request.getContextId().isBlank()) {
            context = contextRepository.findById(UUID.fromString(request.getContextId()))
                    .orElse(null);
            if (context != null && !isStaffOrAdmin(userRole)
                    && !isPubliclyVisible(context.getIsPublished(), context.getDeletedAt())) {
                context = null; // Ignore if not visible
            }
            if (context != null && context.getSchool() != null && !"SYSTEM_ADMIN".equalsIgnoreCase(userRole)) {
                if (user.getSchool() == null || !user.getSchool().getId().equals(context.getSchool().getId())) {
                    context = null; // Not visible across schools
                } else if (!canAccessClassOnly(user, userRole, context.getContentScope(), context.getCreatedBy(),
                        context.getContextId(), EntityType.CONTEXT)) {
                    context = null; // CLASS_ONLY context of a class the user does not belong to
                }
            }
            if (context != null) {
                final UUID targetContextId = context.getContextId();
                boolean belongsToCharacter = character.getHistoricalContexts() != null
                        && character.getHistoricalContexts().stream()
                                .anyMatch(hc -> targetContextId.equals(hc.getContextId()));
                if (!belongsToCharacter) {
                    throw new InvalidRequestException("Nhân vật lịch sử này không tham gia vào bối cảnh/sự kiện đã chọn!");
                }
            }
        }

        boolean isExam = request.getMode() == ChatSessionMode.EXAM;

        ChatSession session = ChatSession.builder()
                .user(user)
                .character(character)
                .historicalContext(context)
                .title(isExam ? "Vấn đáp với " + character.getName() : "")
                .mode(isExam ? ChatSessionMode.EXAM : ChatSessionMode.CHAT)
                .examQuestionCount(isExam ? resolveExamQuestionCount(user, request) : null)
                .sourceSessionId(isExam ? resolveSourceSession(user, character, request) : null)
                .build();

        ChatSession saved = chatSessionRepository.save(session);
        log.info("Chat session created with ID={}", saved.getSessionId());

        return new PreparedSession(
                saved,
                character.getCharacterId().toString(),
                character.getName(),
                CharacterPronouns.selfOf(character),
                CharacterPronouns.addressOf(character),
                context != null ? context.getContextId().toString() : "00000000-0000-0000-0000-000000000000",
                AiServiceClient.buildCharacterPayload(character),
                context != null ? AiServiceClient.buildContextPayload(context) : null);
    }

    @Transactional
    public void deleteSession(String sessionId, String userId, String userRole) {
        log.info("Deleting chat session={} for user={}", sessionId, userId);

        ChatSession session;
        if (isStaffOrAdmin(userRole)) {
            session = chatSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
        } else {
            session = chatSessionRepository
                    .findBySessionIdAndUserUid(UUID.fromString(sessionId), UUID.fromString(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
        }

        chatSessionRepository.delete(session);
        log.info("Chat session deleted: {}", sessionId);
    }

    @Transactional
    public void softDeleteSession(String sessionId, String userId, String userRole) {
        log.info("Soft deleting chat session={} for user={}", sessionId, userId);

        ChatSession session;
        if (isStaffOrAdmin(userRole)) {
            session = chatSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
        } else {
            session = chatSessionRepository
                    .findBySessionIdAndUserUid(UUID.fromString(sessionId), UUID.fromString(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        session.setDeletedAt(now);
        chatSessionRepository.save(session);

        if (session.getMessages() != null) {
            session.getMessages().forEach(msg -> msg.setDeletedAt(now));
        }

        log.info("Chat session soft deleted: {}", sessionId);
    }

    private ChatSessionResponse mapToResponse(ChatSession session, String lastMessage, int messageCount) {
        return ChatSessionResponse.builder()
                .id(session.getSessionId().toString())
                .characterId(safeGetCharacterId(session))
                .contextId(safeGetContextId(session))
                .title(session.getTitle())
                .lastMessage(lastMessage)
                .lastMessageAt(session.getLastMessageAt())
                .messageCount(messageCount)
                .mode(session.getMode().name())
                .examQuestionCount(session.getExamQuestionCount())
                .sourceSessionId(session.getSourceSessionId() != null ? session.getSourceSessionId().toString() : null)
                .build();
    }

    /** The chat an exam is started from must be the learner's own conversation with the same character. */
    private UUID resolveSourceSession(User user, Character character, CreateChatSessionRequest request) {
        if (request.getSourceSessionId() == null || request.getSourceSessionId().isBlank()) {
            return null;
        }
        UUID sourceId;
        try {
            sourceId = UUID.fromString(request.getSourceSessionId());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Phiên trò chuyện nguồn không hợp lệ.");
        }
        ChatSession source = chatSessionRepository.findActiveBySessionIdAndUserUid(sourceId, user.getUid())
                .orElseThrow(() -> new InvalidRequestException("Không tìm thấy phiên trò chuyện để vấn đáp."));
        if (source.getMode() != ChatSessionMode.CHAT
                || !character.getCharacterId().equals(source.getCharacter().getCharacterId())) {
            throw new InvalidRequestException("Chỉ vấn đáp được từ cuộc trò chuyện với cùng nhân vật.");
        }
        return sourceId;
    }

    /**
     * Free B2C tier customers are limited to 2 Exam Mode questions per session.
     * Paid B2C tiers (Plus & Pro) and B2B school users have no Free-tier cap.
     */
    private int resolveExamQuestionCount(User user, CreateChatSessionRequest request) {
        int requested = request.getExamQuestionCount() != null
                ? request.getExamQuestionCount()
                : DEFAULT_EXAM_QUESTION_COUNT;
        if (user != null && user.getRole() == UserRole.CUSTOMER && userTierRepository != null) {
            boolean hasPaidTier = userTierRepository
                    .findCurrentActiveByUid(user.getUid(), LocalDateTime.now())
                    .map(ut -> ut.getTier() != null && ut.getTier().getAmount() != null && ut.getTier().getAmount() > 0)
                    .orElse(false);
            if (!hasPaidTier) {
                return Math.min(requested, FREE_B2C_EXAM_QUESTION_LIMIT);
            }
        }
        return requested;
    }

    private String safeGetCharacterId(ChatSession session) {
        try {
            Character c = session.getCharacter();
            return c != null ? c.getCharacterId().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String safeGetContextId(ChatSession session) {
        try {
            var ctx = session.getHistoricalContext();
            return ctx != null ? ctx.getContextId().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return role != null && ("CONTENT_ADMIN".equalsIgnoreCase(role)
                || "SYSTEM_ADMIN".equalsIgnoreCase(role)
                || "STAFF".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role));
    }

    /**
     * CLASS_ONLY local content: SCHOOL_ADMIN and platform roles always pass, a TEACHER only for
     * content they authored, anyone else (students) only when enrolled in an assigned class.
     */
    private boolean canAccessClassOnly(User user, String userRole, ContentScope scope, User createdBy,
                                       UUID entityId, EntityType entityType) {
        if (scope != ContentScope.CLASS_ONLY || isStaffOrAdmin(userRole)
                || "SYSTEM_ADMIN".equalsIgnoreCase(userRole) || user.getRole() == UserRole.SCHOOL_ADMIN) {
            return true;
        }
        if (user.getRole() == UserRole.TEACHER) {
            return createdBy != null && user.getUid().equals(createdBy.getUid());
        }
        List<UUID> assignedClassIds = classLocalContentRepository.findClassIdsByEntityIdAndEntityType(entityId, entityType);
        if (assignedClassIds.isEmpty()) {
            return false;
        }
        return classStudentRepository.findActiveClassroomsByStudentUid(user.getUid()).stream()
                .anyMatch(classroom -> assignedClassIds.contains(classroom.getId()));
    }

    private boolean isPubliclyVisible(Boolean isPublished, LocalDateTime deletedAt) {
        return Boolean.TRUE.equals(isPublished)
                && deletedAt == null;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ChatScenarioResponse> getScenarios(String search, EventEra era, Pageable pageable) {
        String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        UUID schoolId = null;
        String userId = SecurityUtils.getUserId();
        if (userId != null) {
            try {
                java.util.Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
                if (userOpt.isPresent() && userOpt.get().getSchool() != null) {
                    schoolId = userOpt.get().getSchool().getId();
                }
            } catch (Exception ex) {
                log.debug("Could not resolve schoolId for userId {}: {}", userId, ex.getMessage());
            }
        }

        Page<Object[]> page = characterRepository.findScenarios(trimmedSearch, era, schoolId, pageable);

        List<ChatScenarioResponse> items = page.getContent().stream()
                .map(row -> {
                    Character c = (Character) row[0];
                    HistoricalContext hc = (HistoricalContext) row[1];
                    UUID scenarioId = UUID.nameUUIDFromBytes((c.getCharacterId().toString() + "_" + hc.getContextId().toString()).getBytes());
                    String scope = (c.getSchool() != null || hc.getSchool() != null) ? "SCHOOL" : "GLOBAL";

                    return ChatScenarioResponse.builder()
                            .scenarioId(scenarioId)
                            .characterId(c.getCharacterId())
                            .characterName(c.getName())
                            .characterTitle(c.getTitle())
                            .characterAvatar(c.getImageUrl())
                            .characterPersonality(c.getPersonality())
                            .contextId(hc.getContextId())
                            .contextName(hc.getName())
                            .contextEra(hc.getEra())
                            .contextYear(hc.getYear())
                            .contextImage(hc.getImageUrl())
                            .contextSummary(hc.getDescription())
                            .scope(scope)
                            .build();
                })
                .toList();

        return PaginatedResponse.<ChatScenarioResponse>builder()
                .content(items)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
