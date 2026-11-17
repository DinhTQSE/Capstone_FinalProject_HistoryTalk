package com.historytalk.security;

import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who may read historical contexts, characters and their documents through the shared (non-teacher) endpoints.
 *
 * <ul>
 *   <li>Staff (Content Admin, System Admin) see everything, drafts and deleted rows included.</li>
 *   <li>Global content (no school) is visible to everyone once published and not deleted.</li>
 *   <li>School-owned (local) content is visible only to users of the same school, and only once published,
 *       except that the author always sees their own drafts. Teachers and School Admins are NOT staff here:
 *       they manage local content through /teacher/local-contents and /school-admin/local-contents.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ContentAccessPolicy {

    private final UserRepository userRepository;
    private final HistoricalContextRepository contextRepository;
    private final CharacterRepository characterRepository;

    /** Content staff: the only roles allowed to see drafts of global content and to write through shared endpoints. */
    public static boolean isStaff(String role) {
        if (role == null) return false;
        String r = role.toUpperCase(Locale.ROOT).replace("ROLE_", "");
        return "CONTENT_ADMIN".equals(r) || "SYSTEM_ADMIN".equals(r) || "STAFF".equals(r) || "ADMIN".equals(r);
    }

    public boolean canView(HistoricalContext ctx, String role) {
        if (ctx == null) return false;
        if (isStaff(role)) return true;
        return canViewOwned(ctx.getSchool(), ctx.getIsPublished(), ctx.getDeletedAt(), ctx.getCreatedBy(), currentUser());
    }

    public boolean canView(Character character, String role) {
        if (character == null) return false;
        if (isStaff(role)) return true;
        return canViewOwned(character.getSchool(), character.getIsPublished(), character.getDeletedAt(),
                character.getCreatedBy(), currentUser());
    }

    /** A document is visible when it is not deleted and its parent context / character is visible. */
    public boolean canView(Document doc, String role) {
        return documentFilter(role).test(doc);
    }

    /**
     * Predicate for filtering many documents in one request: the caller and each parent are loaded once.
     */
    public Predicate<Document> documentFilter(String role) {
        if (isStaff(role)) return doc -> doc != null;
        Optional<User> viewer = currentUser();
        Map<UUID, Boolean> parentVisible = new HashMap<>();
        return doc -> {
            if (doc == null || doc.getDeletedAt() != null || doc.getEntityId() == null) return false;
            return parentVisible.computeIfAbsent(doc.getEntityId(), id -> {
                if (doc.getEntityType() == EntityType.CONTEXT) {
                    return contextRepository.findById(id)
                            .map(c -> canViewOwned(c.getSchool(), c.getIsPublished(), c.getDeletedAt(), c.getCreatedBy(), viewer))
                            .orElse(false);
                }
                if (doc.getEntityType() == EntityType.CHARACTER) {
                    return characterRepository.findById(id)
                            .map(c -> canViewOwned(c.getSchool(), c.getIsPublished(), c.getDeletedAt(), c.getCreatedBy(), viewer))
                            .orElse(false);
                }
                return false;
            });
        };
    }

    private boolean canViewOwned(School school, Boolean isPublished, LocalDateTime deletedAt, User createdBy,
                                 Optional<User> viewer) {
        if (deletedAt != null) return false;
        boolean published = Boolean.TRUE.equals(isPublished);
        if (school == null) return published;

        if (viewer.isEmpty()) return false;
        User user = viewer.get();
        if (user.getSchool() == null || !school.getId().equals(user.getSchool().getId())) return false;
        if (published) return true;
        return createdBy != null && user.getUid().equals(createdBy.getUid());
    }

    private Optional<User> currentUser() {
        String userId = SecurityUtils.getUserId();
        if (userId == null) return Optional.empty();
        try {
            return userRepository.findById(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
