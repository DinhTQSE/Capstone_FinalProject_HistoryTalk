package com.historytalk.repository;

import com.historytalk.entity.chat.ChatSession;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    @Query("""
                                                SELECT cs FROM ChatSession cs
                                                JOIN FETCH cs.character c
                                                LEFT JOIN FETCH cs.historicalContext hc
                                                WHERE cs.user.uid = :userId
                                                        AND c.characterId = :characterId
                                                        AND cs.deletedAt IS NULL
                                                ORDER BY cs.lastMessageAt DESC NULLS LAST, cs.createdAt DESC
            """)
    List<ChatSession> findByUserAndCharacter(
            @Param("userId") UUID userId,
            @Param("characterId") UUID characterId);

    Optional<ChatSession> findBySessionIdAndUserUid(UUID sessionId, UUID userId);

    @Query("""
                        SELECT cs FROM ChatSession cs
                        JOIN FETCH cs.character c
                        LEFT JOIN FETCH cs.historicalContext hc
                        WHERE cs.sessionId = :sessionId
                        AND cs.user.uid = :userId
                        AND cs.deletedAt IS NULL
            """)
    Optional<ChatSession> findActiveBySessionIdAndUserUid(@Param("sessionId") UUID sessionId,
                                                          @Param("userId") UUID userId);

    @Query("""
                        SELECT cs FROM ChatSession cs
                        LEFT JOIN FETCH cs.character c
                        LEFT JOIN FETCH cs.historicalContext hc
                        WHERE cs.user.uid = :userId
                        AND (:includeDeleted = true OR cs.deletedAt IS NULL)
                        ORDER BY cs.lastMessageAt DESC NULLS LAST, cs.createdAt DESC
            """)
    List<ChatSession> findAllByUserUid(@Param("userId") UUID userId,
                                       @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT COUNT(cs) FROM ChatSession cs")
    long countCurrent();

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL")
    long countActive();

    List<ChatSession> findByCharacterCharacterId(UUID characterId);

    List<ChatSession> findByHistoricalContextContextId(UUID contextId);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.createdAt >= :from AND cs.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM chat_session
            WHERE created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countSessionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);
}
