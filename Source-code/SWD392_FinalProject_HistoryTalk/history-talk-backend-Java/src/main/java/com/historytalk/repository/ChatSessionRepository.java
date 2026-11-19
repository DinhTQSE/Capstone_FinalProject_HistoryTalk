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

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL")
    long countCurrent();

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.lastMessageAt IS NOT NULL AND cs.lastMessageAt >= :cutoff")
    long countActiveSince(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.user.role IN :roles")
    long countCurrentByRoles(@Param("roles") java.util.Collection<com.historytalk.entity.enums.UserRole> roles);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.lastMessageAt IS NOT NULL AND cs.lastMessageAt >= :cutoff AND cs.user.role IN :roles")
    long countActiveSinceByRoles(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("roles") java.util.Collection<com.historytalk.entity.enums.UserRole> roles);

    List<ChatSession> findByCharacterCharacterId(UUID characterId);

    List<ChatSession> findByHistoricalContextContextId(UUID contextId);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.createdAt >= :from AND cs.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.createdAt >= :from AND cs.createdAt < :to AND cs.user.role IN :roles")
    long countCreatedBetweenByRoles(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("roles") java.util.Collection<com.historytalk.entity.enums.UserRole> roles);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', cs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', cs.created_at)
                    ELSE date_trunc('day', cs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM chat_session cs
            WHERE cs.deleted_at IS NULL
              AND cs.created_at >= :from
              AND cs.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countSessionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', cs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', cs.created_at)
                    ELSE date_trunc('day', cs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM chat_session cs
            JOIN "user" u ON u.uid = cs.uid
            WHERE cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.role IN (:roles)
              AND cs.created_at >= :from
              AND cs.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countSessionsByPeriodAndRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("roles") java.util.Collection<String> roles);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.user.deletedAt IS NULL AND cs.user.school.id = :schoolId")
    long countCurrentBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.user.deletedAt IS NULL AND cs.lastMessageAt IS NOT NULL AND cs.lastMessageAt >= :cutoff AND cs.user.school.id = :schoolId")
    long countActiveSinceBySchoolId(@Param("cutoff") LocalDateTime cutoff, @Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(cs) FROM ChatSession cs WHERE cs.deletedAt IS NULL AND cs.user.deletedAt IS NULL AND cs.createdAt >= :from AND cs.createdAt < :to AND cs.user.school.id = :schoolId")
    long countCreatedBetweenBySchoolId(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', cs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', cs.created_at)
                    ELSE date_trunc('day', cs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM chat_session cs
            JOIN "user" u ON u.uid = cs.uid
            WHERE cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.school_id = :schoolId
              AND cs.created_at >= :from
              AND cs.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countSessionsByPeriodAndSchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);
}
