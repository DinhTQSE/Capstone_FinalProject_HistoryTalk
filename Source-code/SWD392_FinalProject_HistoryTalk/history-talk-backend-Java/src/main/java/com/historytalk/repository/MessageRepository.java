package com.historytalk.repository;

import com.historytalk.entity.chat.Message;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenTrendProjection;
import com.historytalk.repository.dashboard.DashboardTopTokenUserProjection;
import com.historytalk.repository.dashboard.StudentMessageCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m WHERE m.chatSession.sessionId = :sessionId AND (:includeDeleted = true OR m.deletedAt IS NULL) ORDER BY m.createdAt ASC")
    List<Message> findByChatSessionSessionIdOrderByTimestampAsc(@Param("sessionId") UUID sessionId,
                                                                @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatSession.sessionId = :sessionId AND m.isFromAi = false AND m.deletedAt IS NULL")
    long countUserMessagesInSession(@Param("sessionId") UUID sessionId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatSession.sessionId = :sessionId AND m.isFromAi = true AND m.deletedAt IS NULL")
    long countAiMessagesInSession(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT m.chatSession.sessionId, COUNT(m)
            FROM Message m
            WHERE m.chatSession.sessionId IN :sessionIds
            GROUP BY m.chatSession.sessionId
            """)
    List<Object[]> countMessagesBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    @Query("""
            SELECT m.chatSession.sessionId, m.content
            FROM Message m
            WHERE m.chatSession.sessionId IN :sessionIds
              AND m.createdAt = (
                  SELECT MAX(m2.createdAt)
                  FROM Message m2
                  WHERE m2.chatSession.sessionId = m.chatSession.sessionId
              )
            """)
    List<Object[]> findLatestMessageContentBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL")
    long countCurrent();

    @Query("SELECT COUNT(m) FROM Message m WHERE m.isFromAi = :isFromAi AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL")
    long countByAiFlag(@Param("isFromAi") boolean isFromAi);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt >= :from AND m.createdAt < :to AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.role IN :roles")
    long countCurrentByRoles(@Param("roles") Collection<com.historytalk.entity.enums.UserRole> roles);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.isFromAi = :isFromAi AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.role IN :roles")
    long countByAiFlagAndRoles(@Param("isFromAi") boolean isFromAi,
                               @Param("roles") Collection<com.historytalk.entity.enums.UserRole> roles);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt >= :from AND m.createdAt < :to AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.role IN :roles")
    long countCreatedBetweenByRoles(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("roles") Collection<com.historytalk.entity.enums.UserRole> roles);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countMessagesByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.role IN (:roles)
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countMessagesByPeriodAndRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("roles") Collection<String> roles);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND m.created_at >= :from
              AND m.created_at < :to
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.role IN (:roles)
              AND m.created_at >= :from
              AND m.created_at < :to
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensBetweenByRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("roles") Collection<String> roles);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
            COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
            COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardTokenTrendProjection> sumTokensByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
            COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
            COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.role IN (:roles)
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardTokenTrendProjection> sumTokensByPeriodByRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("roles") Collection<String> roles);

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
            LEFT JOIN tier t ON (
                (rt.tier_id IS NOT NULL AND t.tier_id = rt.tier_id)
                OR
                (rt.tier_id IS NULL AND t.amount = 0)
            )
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND t.deleted_at IS NULL
            GROUP BY u.uid, u.user_name, u.email, t.tier_id, t.title, u.token
            ORDER BY "totalTokens" DESC, "promptTokens" DESC, u.user_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopTokenUserProjection> findTopTokenUsers(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit);

    @Query(value = """
            SELECT CAST(c.character_id AS text) AS "characterId",
                   c.name AS name,
                   COALESCE(c.title, '') AS title,
                   COALESCE(c.image_url, '') AS "imageUrl",
                   COUNT(m.message_id) AS "totalMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN 1 ELSE 0 END), 0) AS "userMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN 1 ELSE 0 END), 0) AS "aiMessages"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id AND cs.deleted_at IS NULL
            JOIN character c ON c.character_id = cs.character_id AND c.deleted_at IS NULL
            WHERE m.deleted_at IS NULL
              AND c.school_id IS NULL
            GROUP BY c.character_id, c.name, c.title, c.image_url
            ORDER BY "totalMessages" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<com.historytalk.repository.dashboard.DashboardTopCharacterProjection> findTopCharactersForDashboard(@Param("limit") int limit);

    @Query(value = """
            SELECT CAST(c.character_id AS text) AS "characterId",
                   c.name AS name,
                   COALESCE(c.title, '') AS title,
                   COALESCE(c.image_url, '') AS "imageUrl",
                   COUNT(m.message_id) AS "totalMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN 1 ELSE 0 END), 0) AS "userMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN 1 ELSE 0 END), 0) AS "aiMessages"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id AND cs.deleted_at IS NULL
            JOIN character c ON c.character_id = cs.character_id AND c.deleted_at IS NULL
            WHERE m.deleted_at IS NULL
              AND c.school_id IS NOT NULL
            GROUP BY c.character_id, c.name, c.title, c.image_url
            ORDER BY "totalMessages" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<com.historytalk.repository.dashboard.DashboardTopCharacterProjection> findTopLocalCharactersForDashboard(@Param("limit") int limit);

    @Query(value = """
            SELECT CAST(u.uid AS text) AS uid,
                   u.user_name AS "userName",
                   u.email AS email,
                   CAST(NULL AS text) AS "tierId",
                   COALESCE(CAST(u.role AS text), 'B2B') AS "tierTitle",
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
            WHERE u.deleted_at IS NULL
              AND u.role IN (:roles)
            GROUP BY u.uid, u.user_name, u.email, u.role, u.token
            ORDER BY "totalTokens" DESC, "promptTokens" DESC, u.user_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopTokenUserProjection> findTopTokenUsersByRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("roles") Collection<String> roles,
            @Param("limit") int limit);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE cs.uid = :uid
              AND cs.deleted_at IS NULL
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensForUser(@Param("uid") UUID uid);

    @Query(value = """
            SELECT CAST(cs.character_id AS text) AS "characterId",
                   c.name AS name,
                   COUNT(m.message_id) AS "messageCount",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "tokenUsed"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id AND cs.deleted_at IS NULL
            JOIN character c ON c.character_id = cs.character_id AND c.deleted_at IS NULL
            WHERE cs.uid = :uid
              AND m.deleted_at IS NULL
            GROUP BY cs.character_id, c.name
            ORDER BY "messageCount" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<com.historytalk.repository.dashboard.DashboardUserTopCharacterProjection> findTopCharactersForUser(
            @Param("uid") UUID uid,
            @Param("limit") int limit);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE u.school_id = :schoolId
              AND u.role = 'SCHOOL_STUDENT'
              AND u.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    long sumTokensUsedBySchoolStudents(@Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE u.school_id = :schoolId
              AND u.role = 'TEACHER'
              AND u.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    long sumTokensUsedBySchoolTeachers(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.deletedAt IS NULL AND m.chatSession.user.school.id = :schoolId")
    long countCurrentBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.isFromAi = :isFromAi AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.deletedAt IS NULL AND m.chatSession.user.school.id = :schoolId")
    long countByAiFlagAndSchoolId(@Param("isFromAi") boolean isFromAi, @Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt >= :from AND m.createdAt < :to AND m.deletedAt IS NULL AND m.chatSession.deletedAt IS NULL AND m.chatSession.user.deletedAt IS NULL AND m.chatSession.user.school.id = :schoolId")
    long countCreatedBetweenBySchoolId(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.school_id = :schoolId
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countMessagesByPeriodAndSchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT CAST(c.character_id AS text) AS "characterId",
                   c.name AS name,
                   COALESCE(c.title, '') AS title,
                   COALESCE(c.image_url, '') AS "imageUrl",
                   COUNT(m.message_id) AS "totalMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN 1 ELSE 0 END), 0) AS "userMessages",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN 1 ELSE 0 END), 0) AS "aiMessages"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id AND cs.deleted_at IS NULL
            JOIN character c ON c.character_id = cs.character_id AND c.deleted_at IS NULL
            WHERE m.deleted_at IS NULL
              AND c.school_id = :schoolId
            GROUP BY c.character_id, c.name, c.title, c.image_url
            ORDER BY "totalMessages" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<com.historytalk.repository.dashboard.DashboardTopCharacterProjection> findTopLocalCharactersBySchoolId(
            @Param("schoolId") UUID schoolId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.school_id = :schoolId
              AND m.created_at >= :from
              AND m.created_at < :to
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensBetweenBySchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
                   COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
                   COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.school_id = :schoolId
              AND u.role = :role
              AND m.created_at >= :from
              AND m.created_at < :to
            """, nativeQuery = true)
    DashboardTokenSummaryProjection sumTokensBetweenBySchoolIdAndRole(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("schoolId") UUID schoolId,
            @Param("role") String role);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', m.created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', m.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', m.created_at)
                    ELSE date_trunc('day', m.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COALESCE(SUM(CASE WHEN m.is_from_ai = FALSE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "promptTokens",
            COALESCE(SUM(CASE WHEN m.is_from_ai = TRUE THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "completionTokens",
            COALESCE(SUM(COALESCE(m.token, 0)), 0) AS "totalTokens"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE m.deleted_at IS NULL
              AND cs.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.school_id = :schoolId
              AND m.created_at >= :from
              AND m.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardTokenTrendProjection> sumTokensByPeriodBySchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT CAST(u.uid AS text) AS uid,
                   u.user_name AS "userName",
                   u.email AS email,
                   CAST(NULL AS text) AS "tierId",
                   COALESCE(CAST(u.role AS text), 'SCHOOL_STUDENT') AS "tierTitle",
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
            WHERE u.deleted_at IS NULL
              AND u.school_id = :schoolId
            GROUP BY u.uid, u.user_name, u.email, u.role, u.token
            ORDER BY "totalTokens" DESC, "promptTokens" DESC, u.user_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopTokenUserProjection> findTopTokenUsersBySchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("schoolId") UUID schoolId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE u.school_id = :schoolId
              AND u.role = 'SCHOOL_STUDENT'
              AND m.deleted_at IS NULL
              AND m.created_at >= :since
            """, nativeQuery = true)
    long sumTokensUsedBySchoolStudentsSince(@Param("schoolId") UUID schoolId, @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE u.school_id = :schoolId
              AND u.role = 'TEACHER'
              AND m.deleted_at IS NULL
              AND m.created_at >= :since
            """, nativeQuery = true)
    long sumTokensUsedBySchoolTeachersSince(@Param("schoolId") UUID schoolId, @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE cs.uid = :uid
              AND m.deleted_at IS NULL
              AND m.created_at >= :since
            """, nativeQuery = true)
    long sumTokensUsedByUserSince(@Param("uid") UUID uid, @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT COUNT(m.message_id)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE cs.uid = :uid
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    long countMessagesByUser(@Param("uid") UUID uid);

    @Query(value = """
            SELECT COUNT(m.message_id)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE cs.uid IN (:studentUids)
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    long countMessagesByStudentUids(@Param("studentUids") List<UUID> studentUids);

    @Query(value = """
            SELECT cs.uid AS "studentUid", COUNT(m.message_id) AS "messageCount"
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            WHERE cs.uid IN (:studentUids)
              AND m.deleted_at IS NULL
            GROUP BY cs.uid
            """, nativeQuery = true)
    List<StudentMessageCountProjection> countMessagesGroupByStudentUids(@Param("studentUids") Collection<UUID> studentUids);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            JOIN chat_session cs ON cs.session_id = m.session_id
            JOIN "user" u ON u.uid = cs.uid
            WHERE u.role = 'CUSTOMER'
              AND m.deleted_at IS NULL
            """, nativeQuery = true)
    long sumTokensUsedByCustomerUsers();

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(m.token, 0)), 0)
            FROM message m
            WHERE m.deleted_at IS NULL
            """, nativeQuery = true)
    long sumAllTokensUsed();
}
