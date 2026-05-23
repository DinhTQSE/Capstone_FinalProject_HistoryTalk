package com.historytalk.repository;

import com.historytalk.entity.chat.Message;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
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
}
