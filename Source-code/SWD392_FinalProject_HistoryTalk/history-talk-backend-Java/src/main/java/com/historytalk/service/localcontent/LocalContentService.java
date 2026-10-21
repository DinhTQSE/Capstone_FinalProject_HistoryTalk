package com.historytalk.service.localcontent;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historytalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historytalk.dto.localcontent.*;
import com.historytalk.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Business logic for local (school-owned) history content.
 * Covers both Context and Character local content.
 */
public interface LocalContentService {

    // ── Teacher: Context ─────────────────────────────────────────────────────

    LocalContentResponse createLocalContext(CreateLocalContextRequest request, UserPrincipal principal);

    LocalContentResponse updateLocalContext(String contextId, UpdateLocalContextRequest request, UserPrincipal principal);

    void deleteLocalContext(String contextId, UserPrincipal principal);

    LocalContentResponse getLocalContextById(String contextId, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getLocalContexts(String search, String status, int page, int size, UserPrincipal principal);

    // ── Teacher: Character ───────────────────────────────────────────────────

    LocalContentResponse createLocalCharacter(CreateLocalCharacterRequest request, UserPrincipal principal);

    LocalContentResponse updateLocalCharacter(String characterId, UpdateLocalCharacterRequest request, UserPrincipal principal);

    void deleteLocalCharacter(String characterId, UserPrincipal principal);

    LocalContentResponse getLocalCharacterById(String characterId, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getLocalCharacters(String search, String status, int page, int size, UserPrincipal principal);

    // ── Teacher: Submit / Publish / Status ────────────────────────────────────

    /** Teacher publishes / submits a local context (sets to ACTIVE). */
    LocalContentResponse submitLocalContext(String contextId, UserPrincipal principal);

    /** Teacher publishes / submits a local character (sets to ACTIVE). */
    LocalContentResponse submitLocalCharacter(String characterId, UserPrincipal principal);

    /** Teacher changes status of their own local context (ACTIVE / DRAFT / INACTIVE). */
    LocalContentResponse updateLocalContextStatus(String contextId, UpdateLocalContentStatusRequest request, UserPrincipal principal);

    /** Teacher changes status of their own local character (ACTIVE / DRAFT / INACTIVE). */
    LocalContentResponse updateLocalCharacterStatus(String characterId, UpdateLocalContentStatusRequest request, UserPrincipal principal);

    // ── School Admin: review panel ───────────────────────────────────────────

    PaginatedResponse<LocalContentResponse> getSchoolLocalContexts(String search, String scope, String status, int page, int size, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getSchoolLocalCharacters(String search, String scope, String status, int page, int size, UserPrincipal principal);

    LocalContentResponse approveLocalContext(String contextId, UpdateLocalContentStatusRequest request, UserPrincipal principal);

    LocalContentResponse approveLocalCharacter(String characterId, UpdateLocalContentStatusRequest request, UserPrincipal principal);

    // ── Student: Published content ───────────────────────────────────────────

    PaginatedResponse<LocalContentResponse> getStudentPublishedLocalContexts(String search, String classId, int page, int size, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getStudentPublishedLocalCharacters(String search, String classId, int page, int size, UserPrincipal principal);

    LocalContentResponse getPublishedLocalContextById(String contextId, UserPrincipal principal);

    LocalContentResponse getPublishedLocalCharacterById(String characterId, UserPrincipal principal);

    // ── Document management for local content ────────────────────────────────

    /** List all documents attached to a local context (teacher must belong to same school) */
    List<HistoricalContextDocumentResponse> getLocalContextDocuments(String contextId, UserPrincipal principal);

    /** Attach a new document to a local context */
    HistoricalContextDocumentResponse addLocalContextDocument(String contextId,
            CreateHistoricalContextDocumentRequest request, UserPrincipal principal);

    /** Upload a PDF file directly to a local context (extracts text, uploads PDF to storage, embeds to AI) */
    HistoricalContextDocumentResponse uploadLocalContextPdfDocument(String contextId,
            MultipartFile file, String title, UserPrincipal principal);

    /** Soft-delete a document from a local context (only document creator or school admin) */
    void deleteLocalContextDocument(String contextId, String docId, UserPrincipal principal);

    /** List all documents attached to a local character */
    List<HistoricalContextDocumentResponse> getLocalCharacterDocuments(String characterId, UserPrincipal principal);

    /** Attach a new document to a local character */
    HistoricalContextDocumentResponse addLocalCharacterDocument(String characterId,
            CreateHistoricalContextDocumentRequest request, UserPrincipal principal);

    /** Upload a PDF file directly to a local character (extracts text, uploads PDF to storage, embeds to AI) */
    HistoricalContextDocumentResponse uploadLocalCharacterPdfDocument(String characterId,
            MultipartFile file, String title, UserPrincipal principal);

    /** Soft-delete a document from a local character */
    void deleteLocalCharacterDocument(String characterId, String docId, UserPrincipal principal);

    /** Update an existing document attached to a local context */
    HistoricalContextDocumentResponse updateLocalContextDocument(String contextId, String docId,
            com.historytalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest request, UserPrincipal principal);

    /** Update an existing document attached to a local character */
    HistoricalContextDocumentResponse updateLocalCharacterDocument(String characterId, String docId,
            com.historytalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest request, UserPrincipal principal);

    // ── Dynamic Context-Character Linking ────────────────────────────────────

    void linkCharacterToContext(String characterId, String contextId, UserPrincipal principal);

    void unlinkCharacterFromContext(String characterId, String contextId, UserPrincipal principal);

    List<LocalContentResponse> getCharacterContexts(String characterId, UserPrincipal principal);

    // ── School Trash & Restore ───────────────────────────────────────────────

    PaginatedResponse<LocalContentResponse> getTrashedContexts(int page, int size, UserPrincipal principal);

    LocalContentResponse restoreLocalContext(String contextId, UserPrincipal principal);

    void permanentDeleteLocalContext(String contextId, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getTrashedCharacters(int page, int size, UserPrincipal principal);

    LocalContentResponse restoreLocalCharacter(String characterId, UserPrincipal principal);

    void permanentDeleteLocalCharacter(String characterId, UserPrincipal principal);

    // ── Local Quizzes & Questions ─────────────────────────────────────────────

    PaginatedResponse<com.historytalk.dto.quiz.QuizStaffResponse> getLocalContextQuizzes(
            String contextId, int page, int size, UserPrincipal principal);

    com.historytalk.dto.quiz.QuizStaffResponse createLocalQuiz(
            String contextId, com.historytalk.dto.quiz.CreateQuizRequest request, UserPrincipal principal);

    com.historytalk.dto.quiz.QuizStaffResponse getLocalQuizById(String quizId, UserPrincipal principal);

    com.historytalk.dto.quiz.QuizStaffResponse updateLocalQuiz(
            String quizId, com.historytalk.dto.quiz.UpdateQuizRequest request, UserPrincipal principal);

    void deleteLocalQuiz(String quizId, UserPrincipal principal);

    com.historytalk.dto.quiz.QuizStaffResponse togglePublishLocalQuiz(String quizId, UserPrincipal principal);

    com.historytalk.dto.quiz.QuestionResponse addLocalQuizQuestion(
            String quizId, com.historytalk.dto.quiz.QuestionRequest request, UserPrincipal principal);

    void updateLocalQuizQuestion(
            String quizId, String questionId, com.historytalk.dto.quiz.QuestionRequest request, UserPrincipal principal);

    void deleteLocalQuizQuestion(String quizId, String questionId, UserPrincipal principal);
}
