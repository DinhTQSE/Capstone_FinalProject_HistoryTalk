package com.historytalk.service.localcontent;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historytalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historytalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;
import com.historytalk.dto.quiz.CreateQuizRequest;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.dto.quiz.QuestionResponse;
import com.historytalk.dto.quiz.QuizStaffResponse;
import com.historytalk.dto.quiz.UpdateQuizRequest;
import com.historytalk.dto.localcontent.*;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.entity.localcontent.ClassLocalContent;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link LocalContentService}.
 *
 * <p><b>Business rules enforced here:</b>
 * <ul>
 *   <li>Teacher can only create/edit/delete content belonging to their own school.</li>
 *   <li>Teacher can only edit content they personally created.</li>
 *   <li>SCHOOL_WIDE content is visible to all teachers of the same school (read only for others).</li>
 *   <li>School Admin can change status (DRAFT ↔ PUBLISHED ↔ INACTIVE) for any content in their school.</li>
 *   <li>Students see only PUBLISHED content (enforced at assignment layer).</li>
 *   <li>Platform (HistoryTalk) bears zero responsibility for local content accuracy.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalContentServiceImpl implements LocalContentService {

    private final LocalContextRepository localContextRepo;
    private final LocalCharacterRepository localCharacterRepo;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final DocumentRepository documentRepository;
    private final AiServiceClient aiServiceClient;
    private final ClassLocalContentRepository classLocalContentRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.historytalk.service.historicalContext.strategy.DocumentProcessorFactory documentProcessorFactory;
    private final com.historytalk.repository.QuizRepository quizRepository;
    private final com.historytalk.repository.QuestionRepository questionRepository;
    private final com.historytalk.service.quiz.QuizService quizService;
    private final com.historytalk.repository.classroom.ClassStudentRepository classStudentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.historytalk.service.document.PdfExtractionService pdfExtractionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.historytalk.service.document.SupabaseDocumentStorageService supabaseDocumentStorageService;

    // ══════════════════════════════════════════════════════════════════════════
    // TEACHER — Context
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public LocalContentResponse createLocalContext(CreateLocalContextRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);

        boolean publish = req.getIsPublished() != null ? req.getIsPublished() : true;

        HistoricalContext ctx = HistoricalContext.builder()
                .name(req.getName())
                .description(req.getDescription())
                .era(req.getEra())
                .category(req.getCategory())
                .year(req.getYear())
                .startYear(req.getStartYear())
                .endYear(req.getEndYear())
                .isBC(req.getIsBC() != null ? req.getIsBC() : false)
                .location(req.getLocation())
                .imageUrl(req.getImageUrl())
                .videoUrl(req.getVideoUrl())
                .isPublished(publish)
                .contentStatus(publish ? ContentStatus.ACTIVE : ContentStatus.DRAFT)
                .approvedBy(publish ? teacher : null)
                .school(school)
                .contentScope(req.getContentScope() != null ? req.getContentScope() : ContentScope.CLASS_ONLY)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .createdBy(teacher)
                .battleMap(serializeBattleMap(req.getBattleMap()))
                .build();

        ctx = localContextRepo.save(ctx);
        saveClassAssignments(ctx.getContextId(), EntityType.CONTEXT, req.getClassIds(), teacher.getUid());
        log.info("Teacher {} created local context {} (scope={}, published={}) for school {}",
                teacher.getUid(), ctx.getContextId(), ctx.getContentScope(), ctx.getIsPublished(), school.getId());
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public LocalContentResponse updateLocalContext(String contextId, UpdateLocalContextRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = findLocalContextOwned(contextId, school.getId(), teacher.getUid());

        // Patch only non-null fields
        if (StringUtils.hasText(req.getName()))        ctx.setName(req.getName());
        if (StringUtils.hasText(req.getDescription())) ctx.setDescription(req.getDescription());
        if (req.getEra() != null)                      ctx.setEra(req.getEra());
        if (req.getCategory() != null)                 ctx.setCategory(req.getCategory());
        if (req.getYear() != null)                     ctx.setYear(req.getYear());
        if (req.getStartYear() != null)                ctx.setStartYear(req.getStartYear());
        if (req.getEndYear() != null)                  ctx.setEndYear(req.getEndYear());
        if (req.getIsBC() != null)                     ctx.setIsBC(req.getIsBC());
        if (StringUtils.hasText(req.getLocation()))    ctx.setLocation(req.getLocation());
        if (StringUtils.hasText(req.getImageUrl()))    ctx.setImageUrl(req.getImageUrl());
        if (StringUtils.hasText(req.getVideoUrl()))    ctx.setVideoUrl(req.getVideoUrl());
        if (req.getLatitude() != null)                 ctx.setLatitude(req.getLatitude());
        if (req.getLongitude() != null)                ctx.setLongitude(req.getLongitude());
        if (req.getBattleMap() != null)                ctx.setBattleMap(serializeBattleMap(req.getBattleMap()));

        if (req.getIsPublished() != null) {
            ctx.setIsPublished(req.getIsPublished());
            ctx.setContentStatus(req.getIsPublished() ? ContentStatus.ACTIVE : ContentStatus.DRAFT);
            ctx.setApprovedBy(req.getIsPublished() ? teacher : null);
        }
        if (req.getContentScope() != null)             ctx.setContentScope(req.getContentScope());

        if (req.getClassIds() != null) {
            saveClassAssignments(ctx.getContextId(), EntityType.CONTEXT, req.getClassIds(), teacher.getUid());
        }

        ctx = localContextRepo.save(ctx);
        log.info("Teacher {} updated local context {}", teacher.getUid(), ctx.getContextId());
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public void deleteLocalContext(String contextId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = findLocalContextOwned(contextId, school.getId(), teacher.getUid());
        ctx.setDeletedAt(LocalDateTime.now());
        localContextRepo.save(ctx);
        log.info("Teacher {} soft-deleted local context {}", teacher.getUid(), ctx.getContextId());
    }

    @Override
    @Transactional(readOnly = true)
    public LocalContentResponse getLocalContextById(String contextId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID teacherId = resolveUserId(principal);

        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), school.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));

        // CLASS_ONLY: only owner teacher can see it
        if (ctx.getContentScope() == ContentScope.CLASS_ONLY
                && !ctx.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You do not have access to this local content");
        }
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getLocalContexts(String search, String status, int page, int size, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID teacherId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<HistoricalContext> pageResult = localContextRepo.findVisibleToTeacher(
                school.getId(), teacherId, normalize(search), pageable);

        Page<LocalContentResponse> mapped = pageResult.map(this::mapContextToResponse);
        if (status != null && !status.isBlank()) {
            List<LocalContentResponse> filtered = mapped.getContent().stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            return buildPageFromList(filtered, page, size);
        }
        return buildPage(mapped);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEACHER — Character
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public LocalContentResponse createLocalCharacter(CreateLocalCharacterRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);

        boolean publish = req.getIsPublished() != null ? req.getIsPublished() : true;

        Character character = Character.builder()
                .name(req.getName())
                .title(req.getTitle())
                .background(req.getBackground())
                .personality(req.getPersonality())
                .imageUrl(req.getImageUrl())
                .modelUrl(req.getModelUrl())
                .bornYear(req.getBornYear())
                .bornMonth(req.getBornMonth())
                .bornDay(req.getBornDay())
                .isBornBc(req.getIsBornBc() != null ? req.getIsBornBc() : false)
                .deathYear(req.getDeathYear())
                .deathMonth(req.getDeathMonth())
                .deathDay(req.getDeathDay())
                .isDeathBc(req.getIsDeathBc() != null ? req.getIsDeathBc() : false)
                .isPublished(publish)
                .contentStatus(publish ? ContentStatus.ACTIVE : ContentStatus.DRAFT)
                .approvedBy(publish ? teacher : null)
                .school(school)
                .contentScope(req.getContentScope() != null ? req.getContentScope() : ContentScope.CLASS_ONLY)
                .createdBy(teacher)
                .build();

        // Link to historical contexts if provided
        if (req.getContextIds() != null && !req.getContextIds().isEmpty()) {
            for (String ctxId : req.getContextIds()) {
                localContextRepo.findById(UUID.fromString(ctxId))
                        .ifPresent(hc -> character.getHistoricalContexts().add(hc));
            }
        }

        Character saved = localCharacterRepo.save(character);
        saveClassAssignments(saved.getCharacterId(), EntityType.CHARACTER, req.getClassIds(), teacher.getUid());
        log.info("Teacher {} created local character {} (scope={}, published={}) for school {}",
                teacher.getUid(), saved.getCharacterId(), saved.getContentScope(), saved.getIsPublished(), school.getId());
        return mapCharacterToResponse(saved);
    }

    @Override
    @Transactional
    public LocalContentResponse updateLocalCharacter(String characterId, UpdateLocalCharacterRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        Character character = findLocalCharacterOwned(characterId, school.getId(), teacher.getUid());

        if (StringUtils.hasText(req.getName()))        character.setName(req.getName());
        if (StringUtils.hasText(req.getBackground()))  character.setBackground(req.getBackground());
        if (StringUtils.hasText(req.getTitle()))       character.setTitle(req.getTitle());
        if (StringUtils.hasText(req.getPersonality())) character.setPersonality(req.getPersonality());
        if (StringUtils.hasText(req.getImageUrl()))    character.setImageUrl(req.getImageUrl());
        if (StringUtils.hasText(req.getModelUrl()))    character.setModelUrl(req.getModelUrl());
        if (req.getBornYear() != null)                 character.setBornYear(req.getBornYear());
        if (req.getBornMonth() != null)                character.setBornMonth(req.getBornMonth());
        if (req.getBornDay() != null)                  character.setBornDay(req.getBornDay());
        if (req.getIsBornBc() != null)                 character.setIsBornBc(req.getIsBornBc());
        if (req.getDeathYear() != null)                character.setDeathYear(req.getDeathYear());
        if (req.getDeathMonth() != null)               character.setDeathMonth(req.getDeathMonth());
        if (req.getDeathDay() != null)                 character.setDeathDay(req.getDeathDay());
        if (req.getIsDeathBc() != null)                character.setIsDeathBc(req.getIsDeathBc());

        if (req.getIsPublished() != null) {
            character.setIsPublished(req.getIsPublished());
            character.setContentStatus(req.getIsPublished() ? ContentStatus.ACTIVE : ContentStatus.DRAFT);
            character.setApprovedBy(req.getIsPublished() ? teacher : null);
        }

        if (req.getContentScope() != null) {
            character.setContentScope(req.getContentScope());
        }

        // Update linked contexts if provided
        if (req.getContextIds() != null) {
            character.getHistoricalContexts().clear();
            for (String ctxId : req.getContextIds()) {
                localContextRepo.findById(UUID.fromString(ctxId))
                        .ifPresent(hc -> character.getHistoricalContexts().add(hc));
            }
        }

        if (req.getClassIds() != null) {
            saveClassAssignments(character.getCharacterId(), EntityType.CHARACTER, req.getClassIds(), teacher.getUid());
        }

        Character saved = localCharacterRepo.save(character);
        log.info("Teacher {} updated local character {}", teacher.getUid(), saved.getCharacterId());
        return mapCharacterToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteLocalCharacter(String characterId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        Character character = findLocalCharacterOwned(characterId, school.getId(), teacher.getUid());
        character.setDeletedAt(LocalDateTime.now());
        localCharacterRepo.save(character);
        log.info("Teacher {} soft-deleted local character {}", teacher.getUid(), character.getCharacterId());
    }

    @Override
    @Transactional(readOnly = true)
    public LocalContentResponse getLocalCharacterById(String characterId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID teacherId = resolveUserId(principal);

        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), school.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));

        if (character.getContentScope() == ContentScope.CLASS_ONLY
                && !character.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You do not have access to this local content");
        }
        return mapCharacterToResponse(character);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getLocalCharacters(String search, String status, int page, int size, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID teacherId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.ASC, "name"));

        Page<Character> pageResult = localCharacterRepo.findVisibleToTeacher(
                school.getId(), teacherId, normalize(search), pageable);

        Page<LocalContentResponse> mapped = pageResult.map(this::mapCharacterToResponse);
        if (status != null && !status.isBlank()) {
            List<LocalContentResponse> filtered = mapped.getContent().stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            return buildPageFromList(filtered, page, size);
        }
        return buildPage(mapped);
    }

    // ── Teacher: Submit / Publish / Status ──────────────────────────────────────────
    
    @Override
    @Transactional
    public LocalContentResponse submitLocalContext(String contextId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = findLocalContextOwned(contextId, school.getId(), teacher.getUid());

        ctx.setIsPublished(true);
        ctx.setContentStatus(ContentStatus.ACTIVE);
        ctx.setApprovedBy(teacher);
        ctx = localContextRepo.save(ctx);
        log.info("Teacher {} published local context {}", teacher.getUid(), ctx.getContextId());
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public LocalContentResponse submitLocalCharacter(String characterId, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        Character c = findLocalCharacterOwned(characterId, school.getId(), teacher.getUid());

        c.setIsPublished(true);
        c.setContentStatus(ContentStatus.ACTIVE);
        c.setApprovedBy(teacher);
        Character saved = localCharacterRepo.save(c);
        log.info("Teacher {} published local character {}", teacher.getUid(), saved.getCharacterId());
        return mapCharacterToResponse(saved);
    }

    @Override
    @Transactional
    public LocalContentResponse updateLocalContextStatus(String contextId, UpdateLocalContentStatusRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = findLocalContextOwned(contextId, school.getId(), teacher.getUid());

        applyStatus(ctx, req.getStatus(), req.getNote(), teacher);
        ctx = localContextRepo.save(ctx);
        log.info("Teacher {} set local context {} to status={}", teacher.getUid(), ctx.getContextId(), req.getStatus());
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public LocalContentResponse updateLocalCharacterStatus(String characterId, UpdateLocalContentStatusRequest req, UserPrincipal principal) {
        School school = resolveSchool(principal);
        User teacher = resolveUser(principal);
        Character character = findLocalCharacterOwned(characterId, school.getId(), teacher.getUid());

        applyStatus(character, req.getStatus(), req.getNote(), teacher);
        Character saved = localCharacterRepo.save(character);
        log.info("Teacher {} set local character {} to status={}", teacher.getUid(), saved.getCharacterId(), req.getStatus());
        return mapCharacterToResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCHOOL ADMIN — Review Panel
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getSchoolLocalContexts(
            String search, String scopeStr, String status, int page, int size, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        ContentScope scope = parseScope(scopeStr);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<HistoricalContext> pageResult = localContextRepo.findAllBySchool(
                schoolId, normalize(search), scope, pageable);
        Page<LocalContentResponse> mappedCtx = pageResult.map(this::mapContextToResponse);
        if (status != null && !status.isBlank()) {
            List<LocalContentResponse> filtered = mappedCtx.getContent().stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            return buildPageFromList(filtered, page, size);
        }
        return buildPage(mappedCtx);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getSchoolLocalCharacters(
            String search, String scopeStr, String status, int page, int size, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        ContentScope scope = parseScope(scopeStr);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.ASC, "name"));

        Page<Character> pageResult = localCharacterRepo.findAllBySchool(
                schoolId, normalize(search), scope, pageable);
        Page<LocalContentResponse> mappedChar = pageResult.map(this::mapCharacterToResponse);
        if (status != null && !status.isBlank()) {
            List<LocalContentResponse> filtered = mappedChar.getContent().stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            return buildPageFromList(filtered, page, size);
        }
        return buildPage(mappedChar);
    }

    @Override
    @Transactional
    public LocalContentResponse approveLocalContext(String contextId, UpdateLocalContentStatusRequest req, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User admin = resolveUser(principal);

        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found in your school"));

        applyStatus(ctx, req.getStatus(), req.getNote(), admin);
        ctx = localContextRepo.save(ctx);
        log.info("School Admin {} set local context {} to status={}", admin.getUid(), ctx.getContextId(), req.getStatus());
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public LocalContentResponse approveLocalCharacter(String characterId, UpdateLocalContentStatusRequest req, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User admin = resolveUser(principal);

        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found in your school"));

        applyStatus(character, req.getStatus(), req.getNote(), admin);
        Character saved = localCharacterRepo.save(character);
        log.info("School Admin {} set local character {} to status={}", admin.getUid(), saved.getCharacterId(), req.getStatus());
        return mapCharacterToResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STUDENT — Published content detail
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getStudentPublishedLocalContexts(
            String search, String classId, int page, int size, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID studentId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<UUID> targetClassIds;
        if (StringUtils.hasText(classId)) {
            targetClassIds = List.of(UUID.fromString(classId));
        } else {
            targetClassIds = classStudentRepository.findActiveClassroomsByStudentUid(studentId)
                    .stream()
                    .map(com.historytalk.entity.classroom.Classroom::getId)
                    .toList();
        }

        boolean hasClasses = !targetClassIds.isEmpty();
        List<UUID> safeClassIds = hasClasses ? targetClassIds : List.of(UUID.randomUUID());

        Page<HistoricalContext> pageResult = localContextRepo.findVisibleToStudent(
                school.getId(), hasClasses, safeClassIds, normalize(search), pageable);

        return buildPage(pageResult.map(this::mapContextToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getStudentPublishedLocalCharacters(
            String search, String classId, int page, int size, UserPrincipal principal) {
        School school = resolveSchool(principal);
        UUID studentId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.ASC, "name"));

        List<UUID> targetClassIds;
        if (StringUtils.hasText(classId)) {
            targetClassIds = List.of(UUID.fromString(classId));
        } else {
            targetClassIds = classStudentRepository.findActiveClassroomsByStudentUid(studentId)
                    .stream()
                    .map(com.historytalk.entity.classroom.Classroom::getId)
                    .toList();
        }

        boolean hasClasses = !targetClassIds.isEmpty();
        List<UUID> safeClassIds = hasClasses ? targetClassIds : List.of(UUID.randomUUID());

        Page<Character> pageResult = localCharacterRepo.findVisibleToStudent(
                school.getId(), hasClasses, safeClassIds, normalize(search), pageable);

        return buildPage(pageResult.map(this::mapCharacterToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public LocalContentResponse getPublishedLocalContextById(String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID studentId = resolveUserId(principal);

        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        if (!Boolean.TRUE.equals(ctx.getIsPublished()) || ctx.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Content is not published");
        }

        // If CLASS_ONLY, student must be enrolled in one of the assigned classes
        if (ctx.getContentScope() == ContentScope.CLASS_ONLY) {
            List<UUID> assignedClasses = classLocalContentRepo
                    .findClassIdsByEntityIdAndEntityType(ctx.getContextId(), EntityType.CONTEXT);
            List<UUID> studentClasses = classStudentRepository.findActiveClassroomsByStudentUid(studentId)
                    .stream()
                    .map(com.historytalk.entity.classroom.Classroom::getId)
                    .toList();
            boolean hasAccess = assignedClasses.stream().anyMatch(studentClasses::contains);
            if (!hasAccess) {
                throw new UnauthorizedException("You do not have access to this class-only local content");
            }
        }

        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional(readOnly = true)
    public LocalContentResponse getPublishedLocalCharacterById(String characterId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID studentId = resolveUserId(principal);

        Character c = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        if (!Boolean.TRUE.equals(c.getIsPublished()) || c.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Content is not published");
        }

        // If CLASS_ONLY, student must be enrolled in one of the assigned classes
        if (c.getContentScope() == ContentScope.CLASS_ONLY) {
            List<UUID> assignedClasses = classLocalContentRepo
                    .findClassIdsByEntityIdAndEntityType(c.getCharacterId(), EntityType.CHARACTER);
            List<UUID> studentClasses = classStudentRepository.findActiveClassroomsByStudentUid(studentId)
                    .stream()
                    .map(com.historytalk.entity.classroom.Classroom::getId)
                    .toList();
            boolean hasAccess = assignedClasses.stream().anyMatch(studentClasses::contains);
            if (!hasAccess) {
                throw new UnauthorizedException("You do not have access to this class-only local content");
            }
        }

        return mapCharacterToResponse(c);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void applyStatus(HistoricalContext ctx, ContentStatus status, String note, User admin) {
        ctx.setContentStatus(status);
        if (note != null) ctx.setReviewNote(note);
        switch (status) {
            case ACTIVE -> {
                ctx.setIsPublished(true);
                ctx.setApprovedBy(admin);
                ctx.setDeletedAt(null);
            }
            case DRAFT -> {
                ctx.setIsPublished(false);
                ctx.setApprovedBy(null);
            }
            case INACTIVE -> {
                ctx.setIsPublished(false);
                // Keep approvedBy for audit trail
            }
            case PENDING_REVIEW -> {
                ctx.setIsPublished(false);
                // No admin action, status just recorded
            }
        }
    }

    private void applyStatus(Character character, ContentStatus status, String note, User admin) {
        character.setContentStatus(status);
        if (note != null) character.setReviewNote(note);
        switch (status) {
            case ACTIVE -> {
                character.setIsPublished(true);
                character.setApprovedBy(admin);
                character.setDeletedAt(null);
            }
            case DRAFT -> {
                character.setIsPublished(false);
                character.setApprovedBy(null);
            }
            case INACTIVE -> {
                character.setIsPublished(false);
            }
            case PENDING_REVIEW -> {
                character.setIsPublished(false);
            }
        }
    }

    /** Helper: replace classroom assignments for a local content item. */
    private void saveClassAssignments(UUID entityId, EntityType entityType, List<String> classIds, UUID teacherId) {
        if (classIds == null || classIds.isEmpty()) return;
        classLocalContentRepo.deleteByEntityIdAndEntityType(entityId, entityType);
        classIds.stream()
                .map(id -> ClassLocalContent.builder()
                        .entityId(entityId)
                        .entityType(entityType)
                        .classId(UUID.fromString(id))
                        .assignedBy(teacherId)
                        .build())
                .forEach(classLocalContentRepo::save);
    }

    private HistoricalContext findLocalContextOwned(String contextId, UUID schoolId, UUID teacherId) {
        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        if (!ctx.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only modify content you created");
        }
        return ctx;
    }

    private Character findLocalCharacterOwned(String characterId, UUID schoolId, UUID teacherId) {
        Character c = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        if (!c.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only modify content you created");
        }
        return c;
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            if (principal != null) {
                return principal;
            }
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        return new UserPrincipal(user);
    }

    private UUID resolveUserId(UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        return UUID.fromString(userPrincipal.getUid());
    }

    private School resolveSchool(UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        if (userPrincipal.getSchoolId() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với trường học");
        }
        return schoolRepository.findById(userPrincipal.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found"));
    }

    private UUID resolveSchoolId(UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        if (userPrincipal.getSchoolId() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với trường học");
        }
        return userPrincipal.getSchoolId();
    }

    private User resolveUser(UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        return userRepository.findById(UUID.fromString(userPrincipal.getUid()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ContentScope parseScope(String scopeStr) {
        if (scopeStr == null || scopeStr.isBlank()) return null;
        try {
            return ContentScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid scope value: " + scopeStr);
        }
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }

    private <T> PaginatedResponse<T> buildPage(Page<T> page) {
        return PaginatedResponse.<T>builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /** Build a PaginatedResponse from a pre-filtered in-memory list. */
    private <T> PaginatedResponse<T> buildPageFromList(List<T> list, int requestedPage, int requestedSize) {
        int total = list.size();
        int totalPages = requestedSize > 0 ? (int) Math.ceil((double) total / requestedSize) : 1;
        return PaginatedResponse.<T>builder()
                .content(list)
                .totalElements((long) total)
                .totalPages(totalPages)
                .currentPage(requestedPage)
                .pageSize(requestedSize)
                .hasNext(requestedPage < totalPages)
                .hasPrevious(requestedPage > 1)
                .build();
    }

    // ── Mapper: HistoricalContext → LocalContentResponse ─────────────────────

    private LocalContentResponse mapContextToResponse(HistoricalContext ctx) {
        LocalContentResponse.AuthorInfo createdByInfo = ctx.getCreatedBy() != null
                ? LocalContentResponse.AuthorInfo.builder()
                        .uid(ctx.getCreatedBy().getUid().toString())
                        .userName(ctx.getCreatedBy().getUserName())
                        .fullName(ctx.getCreatedBy().getFullName())
                        .build()
                : null;

        LocalContentResponse.AuthorInfo approvedByInfo = ctx.getApprovedBy() != null
                ? LocalContentResponse.AuthorInfo.builder()
                        .uid(ctx.getApprovedBy().getUid().toString())
                        .userName(ctx.getApprovedBy().getUserName())
                        .fullName(ctx.getApprovedBy().getFullName())
                        .build()
                : null;

        // Prefer stored contentStatus; fall back to derivation for old rows
        ContentStatus status = ctx.getContentStatus() != null
                ? ctx.getContentStatus()
                : (ctx.getDeletedAt() != null ? ContentStatus.INACTIVE
                   : Boolean.TRUE.equals(ctx.getIsPublished()) ? ContentStatus.ACTIVE
                   : ContentStatus.DRAFT);

        List<String> classIds = classLocalContentRepo
                .findClassIdsByEntityIdAndEntityType(ctx.getContextId(), EntityType.CONTEXT)
                .stream().map(UUID::toString).collect(Collectors.toList());

        return LocalContentResponse.builder()
                .id(ctx.getContextId().toString())
                .type("CONTEXT")
                .name(ctx.getName())
                .description(ctx.getDescription())
                .imageUrl(ctx.getImageUrl())
                .status(status)
                .isPublished(ctx.getIsPublished())
                .contentScope(ctx.getContentScope())
                .reviewNote(ctx.getReviewNote())
                .era(ctx.getEra())
                .category(ctx.getCategory())
                .year(ctx.getYear())
                .startYear(ctx.getStartYear())
                .endYear(ctx.getEndYear())
                .isBC(ctx.getIsBC())
                .location(ctx.getLocation())
                .videoUrl(ctx.getVideoUrl())
                .latitude(ctx.getLatitude())
                .longitude(ctx.getLongitude())
                .battleMap(deserializeJson(ctx.getBattleMap()))
                .classIds(classIds.isEmpty() ? null : classIds)
                .createdBy(createdByInfo)
                .approvedBy(approvedByInfo)
                .schoolId(ctx.getSchool() != null ? ctx.getSchool().getId().toString() : null)
                .schoolName(ctx.getSchool() != null ? ctx.getSchool().getName() : null)
                .createdAt(ctx.getCreatedAt())
                .updatedAt(ctx.getUpdatedAt())
                .build();
    }

    // ── Mapper: Character → LocalContentResponse ──────────────────────────────

    private LocalContentResponse mapCharacterToResponse(Character c) {
        LocalContentResponse.AuthorInfo createdByInfo = c.getCreatedBy() != null
                ? LocalContentResponse.AuthorInfo.builder()
                        .uid(c.getCreatedBy().getUid().toString())
                        .userName(c.getCreatedBy().getUserName())
                        .fullName(c.getCreatedBy().getFullName())
                        .build()
                : null;

        LocalContentResponse.AuthorInfo approvedByInfo = c.getApprovedBy() != null
                ? LocalContentResponse.AuthorInfo.builder()
                        .uid(c.getApprovedBy().getUid().toString())
                        .userName(c.getApprovedBy().getUserName())
                        .fullName(c.getApprovedBy().getFullName())
                        .build()
                : null;

        // Prefer stored contentStatus; fall back to derivation for old rows
        ContentStatus status = c.getContentStatus() != null
                ? c.getContentStatus()
                : (c.getDeletedAt() != null ? ContentStatus.INACTIVE
                   : Boolean.TRUE.equals(c.getIsPublished()) ? ContentStatus.ACTIVE
                   : ContentStatus.DRAFT);

        List<String> contextIds = c.getHistoricalContexts() != null
                ? c.getHistoricalContexts().stream()
                        .map(hc -> hc.getContextId().toString())
                        .collect(Collectors.toList())
                : new ArrayList<>();

        List<String> classIds = classLocalContentRepo
                .findClassIdsByEntityIdAndEntityType(c.getCharacterId(), EntityType.CHARACTER)
                .stream().map(UUID::toString).collect(Collectors.toList());

        return LocalContentResponse.builder()
                .id(c.getCharacterId().toString())
                .type("CHARACTER")
                .name(c.getName())
                .description(c.getBackground())
                .imageUrl(c.getImageUrl())
                .status(status)
                .isPublished(c.getIsPublished())
                .contentScope(c.getContentScope())
                .reviewNote(c.getReviewNote())
                .title(c.getTitle())
                .personality(c.getPersonality())
                .modelUrl(c.getModelUrl())
                .bornYear(c.getBornYear())
                .bornMonth(c.getBornMonth())
                .bornDay(c.getBornDay())
                .isBornBc(c.getIsBornBc())
                .deathYear(c.getDeathYear())
                .deathMonth(c.getDeathMonth())
                .deathDay(c.getDeathDay())
                .isDeathBc(c.getIsDeathBc())
                .contextIds(contextIds.isEmpty() ? null : contextIds)
                .classIds(classIds.isEmpty() ? null : classIds)
                .createdBy(createdByInfo)
                .approvedBy(approvedByInfo)
                .schoolId(c.getSchool() != null ? c.getSchool().getId().toString() : null)
                .schoolName(c.getSchool() != null ? c.getSchool().getName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOCUMENT MANAGEMENT — Local Context & Character
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> getLocalContextDocuments(String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        return documentRepository
                .findByEntityIdAndEntityTypeOrderByUploadDateDesc(UUID.fromString(contextId), EntityType.CONTEXT, false)
                .stream().map(this::mapDocumentToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse addLocalContextDocument(
            String contextId, CreateHistoricalContextDocumentRequest req, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));

        DocumentType type = req.getType() != null ? req.getType() : DocumentType.TEXT;
        Document doc = Document.builder()
                .entityId(ctx.getContextId())
                .entityType(EntityType.CONTEXT)
                .createdBy(teacher)
                .title(req.getTitle())
                .fileUrl(req.getFileUrl())
                .content(req.getContent())
                .documentType(type)
                .build();
        Document saved = documentRepository.save(doc);
        aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        log.info("Teacher {} added document {} to local context {}", teacher.getUid(), saved.getDocId(), contextId);
        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse uploadLocalContextPdfDocument(
            String contextId, org.springframework.web.multipart.MultipartFile file, String title, UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File PDF không được để trống");
        }
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        HistoricalContext ctx = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));

        String resolvedTitle = StringUtils.hasText(title) ? title.trim()
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "Tài liệu PDF");

        String extractedText = "";
        if (pdfExtractionService != null) {
            try {
                var extraction = pdfExtractionService.extractText(file);
                if (extraction != null && StringUtils.hasText(extraction.getRawText())) {
                    extractedText = extraction.getRawText();
                }
            } catch (Exception e) {
                log.warn("Failed to extract text from PDF for context {}: {}", contextId, e.getMessage());
            }
        }
        if (!StringUtils.hasText(extractedText)) {
            extractedText = resolvedTitle;
        }

        Document doc = Document.builder()
                .entityId(ctx.getContextId())
                .entityType(EntityType.CONTEXT)
                .createdBy(teacher)
                .title(resolvedTitle)
                .content(extractedText)
                .fileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf")
                .documentType(DocumentType.PDF)
                .build();
        Document saved = documentRepository.save(doc);

        if (supabaseDocumentStorageService != null) {
            try {
                var uploaded = supabaseDocumentStorageService.uploadPdf(EntityType.CONTEXT, ctx.getContextId(), saved.getDocId(), file);
                saved.setFileUrl(uploaded.objectPath());
                saved = documentRepository.save(saved);
            } catch (Exception e) {
                log.warn("Could not upload PDF to Supabase storage (keeping extracted text): {}", e.getMessage());
            }
        }

        aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        log.info("Teacher {} uploaded PDF document {} to local context {}", teacher.getUid(), saved.getDocId(), contextId);
        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteLocalContextDocument(String contextId, String docId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID teacherId = resolveUserId(principal);
        localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!doc.getEntityId().equals(UUID.fromString(contextId))) {
            throw new ResourceNotFoundException("Document does not belong to this context");
        }
        if (!doc.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only delete documents you uploaded");
        }
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("Teacher {} soft-deleted document {} from local context {}", teacherId, docId, contextId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> getLocalCharacterDocuments(String characterId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        return documentRepository
                .findByEntityIdAndEntityTypeOrderByUploadDateDesc(UUID.fromString(characterId), EntityType.CHARACTER, false)
                .stream().map(this::mapDocumentToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse addLocalCharacterDocument(
            String characterId, CreateHistoricalContextDocumentRequest req, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));

        DocumentType type = req.getType() != null ? req.getType() : DocumentType.TEXT;
        Document doc = Document.builder()
                .entityId(character.getCharacterId())
                .entityType(EntityType.CHARACTER)
                .createdBy(teacher)
                .title(req.getTitle())
                .fileUrl(req.getFileUrl())
                .content(req.getContent())
                .documentType(type)
                .build();
        Document saved = documentRepository.save(doc);
        aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        log.info("Teacher {} added document {} to local character {}", teacher.getUid(), saved.getDocId(), characterId);
        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse uploadLocalCharacterPdfDocument(
            String characterId, org.springframework.web.multipart.MultipartFile file, String title, UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File PDF không được để trống");
        }
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));

        String resolvedTitle = StringUtils.hasText(title) ? title.trim()
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "Tài liệu PDF");

        String extractedText = "";
        if (pdfExtractionService != null) {
            try {
                var extraction = pdfExtractionService.extractText(file);
                if (extraction != null && StringUtils.hasText(extraction.getRawText())) {
                    extractedText = extraction.getRawText();
                }
            } catch (Exception e) {
                log.warn("Failed to extract text from PDF for character {}: {}", characterId, e.getMessage());
            }
        }
        if (!StringUtils.hasText(extractedText)) {
            extractedText = resolvedTitle;
        }

        Document doc = Document.builder()
                .entityId(character.getCharacterId())
                .entityType(EntityType.CHARACTER)
                .createdBy(teacher)
                .title(resolvedTitle)
                .content(extractedText)
                .fileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf")
                .documentType(DocumentType.PDF)
                .build();
        Document saved = documentRepository.save(doc);

        if (supabaseDocumentStorageService != null) {
            try {
                var uploaded = supabaseDocumentStorageService.uploadPdf(EntityType.CHARACTER, character.getCharacterId(), saved.getDocId(), file);
                saved.setFileUrl(uploaded.objectPath());
                saved = documentRepository.save(saved);
            } catch (Exception e) {
                log.warn("Could not upload PDF to Supabase storage (keeping extracted text): {}", e.getMessage());
            }
        }

        aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        log.info("Teacher {} uploaded PDF document {} to local character {}", teacher.getUid(), saved.getDocId(), characterId);
        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteLocalCharacterDocument(String characterId, String docId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID teacherId = resolveUserId(principal);
        localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!doc.getEntityId().equals(UUID.fromString(characterId))) {
            throw new ResourceNotFoundException("Document does not belong to this character");
        }
        if (!doc.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only delete documents you uploaded");
        }
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("Teacher {} soft-deleted document {} from local character {}", teacherId, docId, characterId);
    }

    // ── Document Mapper ───────────────────────────────────────────────────────

    private HistoricalContextDocumentResponse mapDocumentToResponse(Document doc) {
        return HistoricalContextDocumentResponse.builder()
                .docId(doc.getDocId().toString())
                .contextId(doc.getEntityId().toString())
                .uid(doc.getCreatedBy() != null ? doc.getCreatedBy().getUid().toString() : null)
                .userName(doc.getCreatedBy() != null ? doc.getCreatedBy().getUserName() : null)
                .title(doc.getTitle())
                .content(doc.getContent())
                .fileUrl(doc.getFileUrl())
                .type(doc.getDocumentType())
                .uploadDate(doc.getUploadedAt())
                .updatedDate(doc.getUpdatedAt())
                .deletedAt(doc.getDeletedAt())
                .build();
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse updateLocalContextDocument(
            String contextId, String docId,
            UpdateHistoricalContextDocumentRequest req,
            UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID teacherId = resolveUserId(principal);
        localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!doc.getEntityId().equals(UUID.fromString(contextId))) {
            throw new ResourceNotFoundException("Document does not belong to this context");
        }
        if (!doc.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only edit documents you uploaded");
        }

        if (StringUtils.hasText(req.getTitle())) {
            doc.setTitle(req.getTitle());
        }
        if (req.getFileUrl() != null) {
            doc.setFileUrl(req.getFileUrl());
        }
        if (req.getType() != null) {
            doc.setDocumentType(req.getType());
        }
        boolean contentChanged = false;
        if (StringUtils.hasText(req.getContent())) {
            var processor = documentProcessorFactory.getStrategy(doc.getDocumentType());
            doc.setContent(processor.processContent(req.getContent()));
            contentChanged = true;
        }
        Document saved = documentRepository.save(doc);
        if (contentChanged) {
            aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        }
        log.info("Teacher {} updated document {} for local context {}", teacherId, docId, contextId);
        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional
    public HistoricalContextDocumentResponse updateLocalCharacterDocument(
            String characterId, String docId,
            UpdateHistoricalContextDocumentRequest req,
            UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        UUID teacherId = resolveUserId(principal);
        localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!doc.getEntityId().equals(UUID.fromString(characterId))) {
            throw new ResourceNotFoundException("Document does not belong to this character");
        }
        if (!doc.getCreatedBy().getUid().equals(teacherId)) {
            throw new UnauthorizedException("You can only edit documents you uploaded");
        }

        if (StringUtils.hasText(req.getTitle())) {
            doc.setTitle(req.getTitle());
        }
        if (req.getFileUrl() != null) {
            doc.setFileUrl(req.getFileUrl());
        }
        if (req.getType() != null) {
            doc.setDocumentType(req.getType());
        }
        boolean contentChanged = false;
        if (StringUtils.hasText(req.getContent())) {
            var processor = documentProcessorFactory.getStrategy(doc.getDocumentType());
            doc.setContent(processor.processContent(req.getContent()));
            contentChanged = true;
        }
        Document saved = documentRepository.save(doc);
        if (contentChanged) {
            aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        }
        log.info("Teacher {} updated document {} for local character {}", teacherId, docId, characterId);
        return mapDocumentToResponse(saved);
    }

    // ── Dynamic Context-Character Linking ────────────────────────────────────

    @Override
    @Transactional
    public void linkCharacterToContext(String characterId, String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        HistoricalContext context = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));

        if (!character.getHistoricalContexts().contains(context)) {
            character.getHistoricalContexts().add(context);
            localCharacterRepo.save(character);
            log.info("Teacher {} linked local character {} to context {}", teacher.getUid(), characterId, contextId);
        }
    }

    @Override
    @Transactional
    public void unlinkCharacterFromContext(String characterId, String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        HistoricalContext context = localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));

        if (character.getHistoricalContexts().remove(context)) {
            localCharacterRepo.save(character);
            log.info("Teacher {} unlinked local character {} from context {}", teacher.getUid(), characterId, contextId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalContentResponse> getCharacterContexts(String characterId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        Character character = localCharacterRepo.findByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local character not found"));
        if (character.getHistoricalContexts() == null) {
            return List.of();
        }
        return character.getHistoricalContexts().stream()
                .filter(ctx -> ctx.getDeletedAt() == null)
                .map(this::mapContextToResponse)
                .toList();
    }

    // ── School Trash & Restore ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getTrashedContexts(int page, int size, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<HistoricalContext> p = localContextRepo.findTrashBySchool(schoolId, pageable);
        return buildPage(p.map(this::mapContextToResponse));
    }

    @Override
    @Transactional
    public LocalContentResponse restoreLocalContext(String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        HistoricalContext ctx = localContextRepo.findTrashedByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Context not found in trash"));
        ctx.setDeletedAt(null);
        ctx.setContentStatus(Boolean.TRUE.equals(ctx.getIsPublished()) ? ContentStatus.ACTIVE : ContentStatus.DRAFT);
        ctx = localContextRepo.save(ctx);
        log.info("Restored local context {} for school {}", contextId, schoolId);
        return mapContextToResponse(ctx);
    }

    @Override
    @Transactional
    public void permanentDeleteLocalContext(String contextId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        HistoricalContext ctx = localContextRepo.findTrashedByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Context not found in trash"));
        localContextRepo.delete(ctx);
        log.info("Permanently deleted local context {} for school {}", contextId, schoolId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LocalContentResponse> getTrashedCharacters(int page, int size, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<Character> p = localCharacterRepo.findTrashBySchool(schoolId, pageable);
        return buildPage(p.map(this::mapCharacterToResponse));
    }

    @Override
    @Transactional
    public LocalContentResponse restoreLocalCharacter(String characterId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        Character c = localCharacterRepo.findTrashedByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found in trash"));
        c.setDeletedAt(null);
        c.setContentStatus(Boolean.TRUE.equals(c.getIsPublished()) ? ContentStatus.ACTIVE : ContentStatus.DRAFT);
        c = localCharacterRepo.save(c);
        log.info("Restored local character {} for school {}", characterId, schoolId);
        return mapCharacterToResponse(c);
    }

    @Override
    @Transactional
    public void permanentDeleteLocalCharacter(String characterId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        Character c = localCharacterRepo.findTrashedByIdAndSchool(UUID.fromString(characterId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found in trash"));
        localCharacterRepo.delete(c);
        log.info("Permanently deleted local character {} for school {}", characterId, schoolId);
    }

    // ── Local Quizzes & Questions ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizStaffResponse> getLocalContextQuizzes(
            String contextId, int page, int size, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<com.historytalk.entity.quiz.Quiz> p = quizRepository.findByContextIdAndDeletedAtIsNull(UUID.fromString(contextId), pageable);
        return buildPage(p.map(this::mapQuizToStaffResponse));
    }

    @Override
    @Transactional
    public QuizStaffResponse createLocalQuiz(
            String contextId, CreateQuizRequest request, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        User teacher = resolveUser(principal);
        localContextRepo.findByIdAndSchool(UUID.fromString(contextId), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Local context not found"));
        request.setContextId(contextId);
        return quizService.createQuiz(request, teacher.getUid());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizStaffResponse getLocalQuizById(String quizId, UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        var quiz = quizRepository.findById(UUID.fromString(quizId))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (quiz.getHistoricalContext() == null || quiz.getHistoricalContext().getSchool() == null
                || !quiz.getHistoricalContext().getSchool().getId().equals(schoolId)) {
            throw new UnauthorizedException("You do not have access to this quiz");
        }
        return quizService.getQuizByIdForStaff(quizId);
    }

    @Override
    @Transactional
    public QuizStaffResponse updateLocalQuiz(String quizId, UpdateQuizRequest request, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        return quizService.updateQuiz(quizId, request);
    }

    @Override
    @Transactional
    public void deleteLocalQuiz(String quizId, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        quizService.softDeleteQuiz(quizId);
    }

    @Override
    @Transactional
    public QuizStaffResponse togglePublishLocalQuiz(String quizId, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        var quiz = quizRepository.findById(UUID.fromString(quizId))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        quiz.setIsPublished(!Boolean.TRUE.equals(quiz.getIsPublished()));
        quizRepository.save(quiz);
        return quizService.getQuizByIdForStaff(quizId);
    }

    @Override
    @Transactional
    public QuestionResponse addLocalQuizQuestion(String quizId, QuestionRequest request, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        return quizService.addQuestion(quizId, request);
    }

    @Override
    @Transactional
    public void updateLocalQuizQuestion(String quizId, String questionId, QuestionRequest request, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        quizService.updateQuestion(quizId, questionId, request);
    }

    @Override
    @Transactional
    public void deleteLocalQuizQuestion(String quizId, String questionId, UserPrincipal principal) {
        getLocalQuizById(quizId, principal); // validates school ownership
        quizService.deleteQuestion(quizId, questionId);
    }

    private QuizStaffResponse mapQuizToStaffResponse(com.historytalk.entity.quiz.Quiz quiz) {
        return quizService.getQuizByIdForStaff(quiz.getQuizId().toString());
    }

    // ────────────────────────────────────────────────────────────────────────
    // battleMap JSON helpers
    // ────────────────────────────────────────────────────────────────────────

    private static final java.util.Set<String> VALID_BATTLE_MAP_MODES = java.util.Set.of("custom", "image");

    private static final java.util.Set<String> VALID_SYMBOL_TYPES = java.util.Set.of(
            "arrow", "flank", "march", "retreat",
            "infantry", "cavalry", "archer", "artillery", "armor",
            "navy", "airforce", "headquarters", "fort", "camp",
            "defenseLine", "stakes", "ambush", "clash", "victory",
            "destroyed", "step", "label"
    );

    private String serializeBattleMap(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            java.util.Map<String, Object> map =
                    objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});

            Object version = map.get("version");
            if (!Integer.valueOf(1).equals(version) && !"1".equals(String.valueOf(version))) {
                throw new InvalidRequestException("battleMap.version phải là 1");
            }

            Object mode = map.get("mode");
            if (mode == null || !VALID_BATTLE_MAP_MODES.contains(mode.toString())) {
                throw new InvalidRequestException(
                        "battleMap.mode phải là 'custom' hoặc 'image'. Nhận: " + mode);
            }

            Object imageUrl = map.get("imageUrl");
            if (imageUrl == null || imageUrl.toString().isBlank()) {
                throw new InvalidRequestException("battleMap.imageUrl không được để trống");
            }

            Object imageSource = map.get("imageSource");
            if (imageSource != null && imageSource.toString().length() > 500) {
                throw new InvalidRequestException("battleMap.imageSource tối đa 500 ký tự");
            }

            Object symbolsRaw = map.get("symbols");
            if (symbolsRaw instanceof java.util.List<?> symbols) {
                for (Object sym : symbols) {
                    if (sym instanceof java.util.Map<?, ?> symMap) {
                        Object symType = symMap.get("type");
                        if (symType != null && !VALID_SYMBOL_TYPES.contains(symType.toString())) {
                            throw new InvalidRequestException(
                                    "battleMap.symbols[].type không hợp lệ: " + symType);
                        }
                    }
                }
            }

            return json;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new InvalidRequestException("battleMap không phải JSON hợp lệ: " + ex.getMessage());
        }
    }

    private Object deserializeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Object>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Could not deserialize battleMap: {}", ex.getMessage());
            return json;
        }
    }
}
