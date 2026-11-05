package com.historytalk.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ExamVerdict;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.historytalk.service.chat.AiServiceClient.ExamGradeResult;
import com.historytalk.service.chat.AiServiceClient.ExamQuestionResult;
import com.historytalk.service.gamification.GamificationService;
import com.historytalk.service.school.token.DailyTokenAllowanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSessionServiceImpl implements ExamSessionService {

    /** Both web and mobile render the parts around this separator as separate chat bubbles. */
    private static final String BUBBLE_SEPARATOR = "\n---\n";
    private static final String NO_CONTEXT_ID = "00000000-0000-0000-0000-000000000000";

    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final GamificationService gamificationService;
    private final DailyTokenAllowanceService dailyTokenAllowanceService;

    @Override
    public Message startExam(ChatSession session) {
        ExamQuestionResult first = askQuestion(session, List.of());

        String opening = "Ta là " + session.getCharacter().getName() + ". Hôm nay ta sẽ hỏi ngươi "
                + session.getExamQuestionCount() + " câu. Hãy trả lời theo hiểu biết của ngươi.";

        Message message = Message.builder()
                .content(opening + BUBBLE_SEPARATOR + first.question())
                .isFromAi(true)
                .chatSession(session)
                .examQuestion(first.question())
                .examExpectedAnswer(first.expectedAnswer())
                .examSource(first.sourceExcerpt())
                .build();
        Message saved = messageRepository.save(message);

        session.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(session);
        return saved;
    }

    @Override
    public ExamTurnResult submitAnswer(ChatSession session, SendMessageRequest request) {
        List<Message> existingMessages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false);

        Message current = existingMessages.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsFromAi()))
                .reduce((first, second) -> second)
                .filter(m -> m.getExamQuestion() != null)
                .orElseThrow(() -> new InvalidRequestException("Buổi vấn đáp này đã kết thúc."));

        String messageType = request.getMessageType() != null ? request.getMessageType() : "TEXT";

        Message userMessage = messageRepository.save(Message.builder()
                .content(request.getContent())
                .isFromAi(false)
                .chatSession(session)
                .messageType(messageType)
                .build());

        ExamGradeResult grade = aiServiceClient.gradeExamAnswer(
                session.getCharacter().getCharacterId().toString(),
                contextIdOf(session),
                characterPayloadOf(session),
                contextPayloadOf(session),
                current.getExamQuestion(),
                current.getExamExpectedAnswer(),
                current.getExamSource(),
                request.getContent());
        ExamVerdict verdict = parseVerdict(grade.verdict());

        int promptTokens = tokensOf(grade.tokenUsage() != null ? grade.tokenUsage().promptTokens() : null);
        int completionTokens = tokensOf(grade.tokenUsage() != null ? grade.tokenUsage().completionTokens() : null);

        // The excerpt held the answer, so it only becomes a visible citation once the question is graded.
        Message assistantMessage = Message.builder()
                .isFromAi(true)
                .chatSession(session)
                .messageType(messageType)
                .examVerdict(verdict)
                .quotes(serializeQuotes(current.getExamSource()))
                .build();

        List<Message> messagesAfterTurn = new ArrayList<>(existingMessages);
        messagesAfterTurn.add(assistantMessage);
        ExamProgressResponse progress = getProgress(session, messagesAfterTurn);

        String nextPart;
        if (progress.isFinished()) {
            nextPart = closingText(progress);
        } else {
            ExamQuestionResult next = askQuestion(session, existingMessages);
            assistantMessage.setExamQuestion(next.question());
            assistantMessage.setExamExpectedAnswer(next.expectedAnswer());
            assistantMessage.setExamSource(next.sourceExcerpt());
            promptTokens += tokensOf(next.tokenUsage() != null ? next.tokenUsage().promptTokens() : null);
            completionTokens += tokensOf(next.tokenUsage() != null ? next.tokenUsage().completionTokens() : null);
            nextPart = next.question();
        }

        assistantMessage.setContent(grade.feedback() + BUBBLE_SEPARATOR + nextPart);
        assistantMessage.setToken(completionTokens);
        Message savedAssistant = messageRepository.save(assistantMessage);

        userMessage.setToken(promptTokens);
        messageRepository.save(userMessage);

        int remainingTokens = chargeTokens(session.getUser(), promptTokens + completionTokens);

        session.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        gamificationService.recordProgress(session.getUser().getUid().toString(), QuestType.CHAT);

        return new ExamTurnResult(userMessage, savedAssistant, progress, remainingTokens, promptTokens, completionTokens);
    }

    @Override
    public ExamProgressResponse getProgress(ChatSession session, List<Message> messages) {
        int correct = 0;
        int partial = 0;
        int incorrect = 0;
        for (Message message : messages) {
            if (message.getExamVerdict() == null) {
                continue;
            }
            switch (message.getExamVerdict()) {
                case CORRECT -> correct++;
                case PARTIAL -> partial++;
                case INCORRECT -> incorrect++;
            }
        }

        int total = session.getExamQuestionCount() != null ? session.getExamQuestionCount() : 0;
        int answered = correct + partial + incorrect;
        double score = total > 0 ? Math.round((correct + partial * 0.5) * 100.0 / total) / 10.0 : 0.0;

        return ExamProgressResponse.builder()
                .totalQuestions(total)
                .answeredQuestions(answered)
                .correctCount(correct)
                .partialCount(partial)
                .incorrectCount(incorrect)
                .score(score)
                .finished(answered >= total)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ExamQuestionResult askQuestion(ChatSession session, List<Message> existingMessages) {
        List<Message> questionMessages = existingMessages.stream()
                .filter(m -> m.getExamQuestion() != null)
                .toList();

        return aiServiceClient.generateExamQuestion(
                session.getCharacter().getCharacterId().toString(),
                contextIdOf(session),
                characterPayloadOf(session),
                contextPayloadOf(session),
                questionMessages.stream().map(Message::getExamQuestion).toList(),
                questionMessages.stream().map(Message::getExamSource).filter(Objects::nonNull).toList());
    }

    private CharacterPayload characterPayloadOf(ChatSession session) {
        return AiServiceClient.buildCharacterPayload(session.getCharacter());
    }

    private ContextPayload contextPayloadOf(ChatSession session) {
        return session.getHistoricalContext() != null
                ? AiServiceClient.buildContextPayload(session.getHistoricalContext())
                : null;
    }

    private String contextIdOf(ChatSession session) {
        return session.getHistoricalContext() != null
                ? session.getHistoricalContext().getContextId().toString()
                : NO_CONTEXT_ID;
    }

    private ExamVerdict parseVerdict(String verdict) {
        try {
            return ExamVerdict.valueOf(verdict);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SystemException("Dịch vụ AI trả về kết quả chấm không hợp lệ: " + verdict);
        }
    }

    private int tokensOf(Integer tokens) {
        return tokens != null ? tokens : 0;
    }

    private String serializeQuotes(String source) {
        try {
            return objectMapper.writeValueAsString(List.of(source));
        } catch (JsonProcessingException | NullPointerException e) {
            log.warn("Failed to serialize exam source: {}", e.getMessage());
            return null;
        }
    }

    private String closingText(ExamProgressResponse progress) {
        return "Buổi vấn đáp đã kết thúc. Ngươi trả lời đúng " + progress.getCorrectCount()
                + " câu, đúng một phần " + progress.getPartialCount()
                + " câu, sai " + progress.getIncorrectCount()
                + " câu trên tổng " + progress.getTotalQuestions()
                + " câu. Điểm của ngươi: " + progress.getScore() + "/10.";
    }

    private int chargeTokens(User user, int totalTokens) {
        int remainingTokens = user.getToken() != null ? user.getToken() : 0;
        if (totalTokens <= 0) {
            return remainingTokens;
        }
        if (user.getRole() == UserRole.CUSTOMER) {
            userRepository.deductTokens(user.getUid(), totalTokens);
            remainingTokens = Math.max(0, remainingTokens - totalTokens);
            user.setToken(remainingTokens);
        } else if (user.getRole() == UserRole.SCHOOL_STUDENT || user.getRole() == UserRole.TEACHER) {
            remainingTokens = dailyTokenAllowanceService.recordStudentTokenUsage(user, totalTokens);
        }
        return remainingTokens;
    }
}
