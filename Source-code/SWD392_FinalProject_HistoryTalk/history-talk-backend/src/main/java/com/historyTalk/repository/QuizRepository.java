package com.historyTalk.repository;

import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.entity.quiz.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

       @Query("SELECT q FROM Quiz q WHERE q.quizId = :quizId AND q.deletedAt IS NULL")
       Optional<Quiz> findActiveById(@Param("quizId") UUID quizId);

    Optional<Quiz> findByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndQuizIdNot(String title, UUID quizId);

    Page<Quiz> findByCreatedByUid(UUID uid, Pageable pageable);

    @Query("""
           SELECT q FROM Quiz q
           WHERE (:search IS NULL OR :search = ''
                  OR q.title ILIKE CONCAT('%', :search, '%')
                  OR q.description ILIKE CONCAT('%', :search, '%'))
           AND (:grade IS NULL OR q.grade = :grade)
           AND (:era IS NULL OR q.era = :era)
           AND (:includeDeleted = true OR q.deletedAt IS NULL)
           """)
    Page<Quiz> findAllWithSearch(
            @Param("search") String search,
            @Param("grade") Integer grade,
            @Param("era") EventEra era,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

    @Query("""
          SELECT q FROM Quiz q
          WHERE q.historicalContext.contextId = :contextId
            AND (:search IS NULL OR :search = ''
                OR q.title ILIKE CONCAT('%', :search, '%')
                OR q.description ILIKE CONCAT('%', :search, '%'))
          AND (:grade IS NULL OR q.grade = :grade)
          AND (:era IS NULL OR q.era = :era)
          AND (:includeDeleted = true OR q.deletedAt IS NULL)
          """)
    Page<Quiz> findAllByContextWithSearch(
           @Param("contextId") UUID contextId,
           @Param("search") String search,
           @Param("grade") Integer grade,
           @Param("era") EventEra era,
           @Param("includeDeleted") boolean includeDeleted,
           Pageable pageable);

    @Query("""
           SELECT q FROM Quiz q
           WHERE (:search IS NULL OR :search = ''
                  OR q.title ILIKE CONCAT('%', :search, '%')
                  OR q.description ILIKE CONCAT('%', :search, '%'))
           AND (:includeDeleted = true OR q.deletedAt IS NULL)
           ORDER BY q.playCount DESC
           """)
    List<Quiz> findAllSimple(@Param("search") String search,
                             @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT q FROM Quiz q WHERE UPPER(q.title) = UPPER(:title) AND q.deletedAt IS NULL")
    Optional<Quiz> findActiveByTitleIgnoreCase(@Param("title") String title);

}
