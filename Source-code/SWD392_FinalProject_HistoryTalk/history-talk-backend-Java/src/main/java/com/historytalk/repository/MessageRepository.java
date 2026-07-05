package com.historytalk.repository;

import com.historytalk.entity.chat.Message;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenTrendProjection;
import com.historytalk.repository.dashboard.DashboardTopTokenUserProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m WHERE m.chatSession.sessionId = :sessionId AND (:includeDeleted = true OR m.deletedAt IS NULL) ORDER BY m.createdAt ASC")
    List<Message> findByChatSessionSessionIdOrderByTimestampAsc(@Param("sessionId") UUID sessionId,
                                                                @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT COUNT(m) FROM Message m")
    long countCurrent();

    @Query("SELECT COUNT(m) FROM Message m WHERE m.isFromAi = :isFromAi")
    long countByAiFlag(@Param("isFromAi") boolean isFromAi);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt >= :from AND m.createdAt < :to")
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
            FROM message
            WHERE created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countMessagesByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN is_from_ai = FALSE THEN COALESCE(token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN is_from_ai = TRUE THEN COALESCE(token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(token, 0)), 0) AS "totalTokens"
            FROM message
            WHERE deleted_at IS NULL
              AND created_at >= :from
              AND created_at < :to
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COALESCE(SUM(CASE WHEN is_from_ai = FALSE THEN COALESCE(token, 0) ELSE 0 END), 0) AS "promptTokens",
            COALESCE(SUM(CASE WHEN is_from_ai = TRUE THEN COALESCE(token, 0) ELSE 0 END), 0) AS "completionTokens",
            COALESCE(SUM(COALESCE(token, 0)), 0) AS "totalTokens"
            FROM message
            WHERE deleted_at IS NULL
              AND created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardTokenTrendProjection> sumTokensByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT CAST(u.uid AS text) AS uid,
                   u.user_name AS "userName",
                   u.email AS email,
                   CAST(t.tier_id AS text) AS "tierId",
                   COALESCE(t.title, 'free') AS "tierTitle",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens",
                   COALESCE(u.token, 0) AS "remainingTokens"
            FROM "user" u
            JOIN chat_session cs
              ON cs.uid = u.uid
             AND cs.deleted_at IS NULL
            JOIN message m
              ON m.session_id = cs.session_id
             AND m.deleted_at IS NULL
             AND m.created_at >= :from
             AND m.created_at < :to
            LEFT JOIN (
                SELECT ut.uid, ut.tier_id,
                       ROW_NUMBER() OVER(PARTITION BY ut.uid ORDER BY t2.amount DESC) as rn
                FROM user_tier ut
                JOIN tier t2 ON t2.tier_id = ut.tier_id
                WHERE ut.deleted_at IS NULL
                  AND ut.is_active = true
                  AND ut.end_time > CURRENT_TIMESTAMP
                  AND t2.deleted_at IS NULL
            ) rt ON rt.uid = u.uid AND rt.rn = 1
            LEFT JOIN tier t ON t.tier_id = rt.tier_id
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
            GROUP BY u.uid, u.user_name, u.email, t.tier_id, t.title, u.token
            ORDER BY "totalTokens" DESC, "promptTokens" DESC, u.user_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopTokenUserProjection> findTopTokenUsers(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit);
}
