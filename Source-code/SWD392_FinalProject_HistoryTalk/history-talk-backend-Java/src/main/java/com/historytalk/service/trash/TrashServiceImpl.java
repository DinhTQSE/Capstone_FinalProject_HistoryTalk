package com.historytalk.service.trash;

import com.historytalk.dto.trash.BulkTrashActionResponse;
import com.historytalk.dto.trash.TrashItemResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {

    private static final String RESTORED = "RESTORED";
    private static final String HARD_DELETED = "HARD_DELETED";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String NOT_TRASHED = "NOT_TRASHED";

    private final CharacterRepository characterRepository;
    private final HistoricalContextRepository contextRepository;
    private final QuizRepository quizRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TrashItemResponse> getDeletedCharacters() {
        return characterRepository.findAllDeleted().stream()
                .map(c -> trashItem(c.getCharacterId(), "CHARACTER", c.getName(), c.getDeletedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrashItemResponse> getDeletedContexts() {
        return contextRepository.findAllDeleted().stream()
                .map(c -> trashItem(c.getContextId(), "HISTORICAL_CONTEXT", c.getName(), c.getDeletedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrashItemResponse> getDeletedQuizzes() {
        return quizRepository.findAllDeleted().stream()
                .map(q -> trashItem(q.getQuizId(), "QUIZ", q.getTitle(), q.getDeletedAt()))
                .toList();
    }

    @Override
    @Transactional
    public BulkTrashActionResponse restoreCharacters(List<String> ids) {
        return apply(ids, id -> characterRepository.findById(id)
                .map(character -> {
                    if (character.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Character is not in trash");
                    }
                    character.setDeletedAt(null);
                    return result(id, RESTORED, "Character restored");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Character not found")));
    }

    @Override
    @Transactional
    public BulkTrashActionResponse restoreContexts(List<String> ids) {
        return apply(ids, id -> contextRepository.findById(id)
                .map(context -> {
                    if (context.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Historical context is not in trash");
                    }
                    context.setDeletedAt(null);
                    return result(id, RESTORED, "Historical context restored");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Historical context not found")));
    }

    @Override
    @Transactional
    public BulkTrashActionResponse restoreQuizzes(List<String> ids) {
        return apply(ids, id -> quizRepository.findById(id)
                .map(quiz -> {
                    if (quiz.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Quiz is not in trash");
                    }
                    quiz.setDeletedAt(null);
                    return result(id, RESTORED, "Quiz restored");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Quiz not found")));
    }

    @Override
    @Transactional
    public BulkTrashActionResponse hardDeleteCharacters(List<String> ids) {
        return apply(ids, id -> characterRepository.findById(id)
                .map(character -> {
                    if (character.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Character is not in trash");
                    }
                    hardDeleteCharacter(character);
                    return result(id, HARD_DELETED, "Character permanently deleted");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Character not found")));
    }

    @Override
    @Transactional
    public BulkTrashActionResponse hardDeleteContexts(List<String> ids) {
        return apply(ids, id -> contextRepository.findById(id)
                .map(context -> {
                    if (context.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Historical context is not in trash");
                    }
                    hardDeleteContext(context);
                    return result(id, HARD_DELETED, "Historical context permanently deleted");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Historical context not found")));
    }

    @Override
    @Transactional
    public BulkTrashActionResponse hardDeleteQuizzes(List<String> ids) {
        return apply(ids, id -> quizRepository.findById(id)
                .map(quiz -> {
                    if (quiz.getDeletedAt() == null) {
                        return result(id, NOT_TRASHED, "Quiz is not in trash");
                    }
                    quizRepository.delete(quiz);
                    return result(id, HARD_DELETED, "Quiz permanently deleted");
                })
                .orElseGet(() -> result(id, NOT_FOUND, "Quiz not found")));
    }

    private void hardDeleteCharacter(Character character) {
        documentRepository.findByEntityIdAndEntityType(character.getCharacterId(), EntityType.CHARACTER)
                .forEach(documentRepository::delete);
        chatSessionRepository.findByCharacterCharacterId(character.getCharacterId())
                .forEach(chatSessionRepository::delete);
        character.getHistoricalContexts().clear();
        characterRepository.delete(character);
    }

    private void hardDeleteContext(HistoricalContext context) {
        documentRepository.findByEntityIdAndEntityType(context.getContextId(), EntityType.CONTEXT)
                .forEach(documentRepository::delete);
        chatSessionRepository.findByHistoricalContextContextId(context.getContextId())
                .forEach(chatSessionRepository::delete);
        context.getCharacters().clear();
        contextRepository.delete(context);
    }

    private TrashItemResponse trashItem(UUID id, String type, String title, java.time.LocalDateTime deletedAt) {
        return TrashItemResponse.builder()
                .id(id.toString())
                .type(type)
                .title(title)
                .status(ContentStatus.INACTIVE)
                .deletedAt(deletedAt)
                .build();
    }

    private BulkTrashActionResponse apply(
            List<String> rawIds,
            Function<UUID, BulkTrashActionResponse.ItemResult> action) {
        List<UUID> ids = normalizeIds(rawIds);
        List<BulkTrashActionResponse.ItemResult> results = new ArrayList<>();
        for (UUID id : ids) {
            results.add(action.apply(id));
        }
        long succeeded = results.stream()
                .filter(r -> RESTORED.equals(r.getStatus()) || HARD_DELETED.equals(r.getStatus()))
                .count();
        return BulkTrashActionResponse.builder()
                .requested(ids.size())
                .succeeded((int) succeeded)
                .results(results)
                .build();
    }

    private List<UUID> normalizeIds(List<String> rawIds) {
        return rawIds == null ? List.of() : rawIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .map(UUID::fromString)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private BulkTrashActionResponse.ItemResult result(UUID id, String status, String message) {
        return BulkTrashActionResponse.ItemResult.builder()
                .id(id.toString())
                .status(status)
                .message(message)
                .build();
    }
}
