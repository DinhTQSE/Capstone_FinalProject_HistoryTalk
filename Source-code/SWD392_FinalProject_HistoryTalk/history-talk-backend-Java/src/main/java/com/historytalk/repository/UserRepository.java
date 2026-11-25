package com.historytalk.repository;

import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceByTierProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Row lock on the user, used to serialise per-user writes such as the daily token request. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.uid = :uid")
    Optional<User> findByIdForUpdate(@Param("uid") UUID uid);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.school WHERE u.uid = :uid")
    Optional<User> findByIdWithSchool(@Param("uid") UUID uid);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String userName);

    Optional<User> findByPasswordResetTokenHash(String tokenHash);

    Optional<User> findByEmailVerificationTokenHash(String tokenHash);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserNameIgnoreCase(String userName);

    Optional<User> findBySchoolIdAndStudentCodeIgnoreCase(UUID schoolId, String studentCode);

    Optional<User> findBySchoolIdAndEmailIgnoreCase(UUID schoolId, String email);

    boolean existsBySchoolIdAndStudentCodeIgnoreCase(UUID schoolId, String studentCode);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school"})
    org.springframework.data.domain.Page<User> findBySchoolIdAndRole(UUID schoolId, UserRole role, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school"})
    List<User> findBySchoolIdAndRoleAndDeletedAtIsNull(UUID schoolId, UserRole role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.role = :role AND u.deletedAt IS NULL")
    long countBySchoolIdAndRole(@Param("schoolId") UUID schoolId, @Param("role") UserRole role);

    boolean existsByEmailIgnoreCaseAndUidNot(String email, UUID uid);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school"})
    @Query("SELECT u FROM User u WHERE u.school.id = :schoolId AND u.role = :role " +
           "AND (:active IS NULL OR (:active = true AND u.deletedAt IS NULL) OR (:active = false AND u.deletedAt IS NOT NULL)) " +
           "AND (CAST(:search AS string) IS NULL OR :search = '' " +
           "OR u.fullName ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR u.userName ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR u.email ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR (u.studentCode IS NOT NULL AND u.studentCode ILIKE CONCAT('%', CAST(:search AS string), '%')))")
    org.springframework.data.domain.Page<User> searchSchoolUsersWithFilter(
            @Param("schoolId") UUID schoolId,
            @Param("role") UserRole role,
            @Param("active") Boolean active,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school"})
    @Query("SELECT u FROM User u WHERE u.school.id = :schoolId AND u.role = 'SCHOOL_STUDENT' AND u.deletedAt IS NULL " +
           "AND (u.accountStatus IS NULL OR u.accountStatus <> com.historytalk.entity.enums.AccountStatus.DEACTIVATED) " +
           "AND u.uid NOT IN (SELECT cs.student.uid FROM ClassStudent cs WHERE cs.status = 'ACTIVE' " +
           "AND cs.classroom.deletedAt IS NULL AND (cs.classroom.status = 'ACTIVE' OR cs.classroom.id = :classroomId))")
    org.springframework.data.domain.Page<User> findAvailableStudentsForClassroom(
            @Param("schoolId") UUID schoolId,
            @Param("classroomId") UUID classroomId,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school"})
    @Query("SELECT u FROM User u WHERE u.school.id = :schoolId AND u.role = 'SCHOOL_STUDENT' AND u.deletedAt IS NULL " +
           "AND (u.accountStatus IS NULL OR u.accountStatus <> com.historytalk.entity.enums.AccountStatus.DEACTIVATED) " +
           "AND u.uid NOT IN (SELECT cs.student.uid FROM ClassStudent cs WHERE cs.status = 'ACTIVE' " +
           "AND cs.classroom.deletedAt IS NULL AND (cs.classroom.status = 'ACTIVE' OR cs.classroom.id = :classroomId)) " +
           "AND (CAST(:search AS string) IS NULL OR :search = '' " +
           "OR u.fullName ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR u.userName ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR u.email ILIKE CONCAT('%', CAST(:search AS string), '%') " +
           "OR (u.studentCode IS NOT NULL AND u.studentCode ILIKE CONCAT('%', CAST(:search AS string), '%')))")
    org.springframework.data.domain.Page<User> searchAvailableStudentsForClassroom(
            @Param("schoolId") UUID schoolId,
            @Param("classroomId") UUID classroomId,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.token = CASE WHEN (u.token - :tokensToDeduct) < 0 THEN 0 ELSE (u.token - :tokensToDeduct) END WHERE u.uid = :userId")
    int deductTokens(@Param("userId") UUID userId, @Param("tokensToDeduct") Integer tokensToDeduct);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.token = COALESCE(u.token, 0) + :tokensToAdd WHERE u.uid = :userId")
    int addTokens(@Param("userId") UUID userId, @Param("tokensToAdd") Integer tokensToAdd);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    long countAllUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    long countActiveUsers();

    default long countInactiveUsers() {
        return 0L;
    }

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NOT NULL")
    long countDeletedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.role = :role")
    long countActiveUsersByRole(@Param("role") UserRole role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.role IN :roles")
    long countActiveUsersByRoles(@Param("roles") java.util.Collection<UserRole> roles);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NOT NULL AND u.role IN :roles")
    long countDeletedUsersByRoles(@Param("roles") java.util.Collection<UserRole> roles);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.createdAt >= :from AND u.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.role IN :roles AND u.createdAt >= :from AND u.createdAt < :to")
    long countCreatedBetweenByRoles(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("roles") java.util.Collection<UserRole> roles);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.lastActiveDate >= :from")
    long countRecentlyActiveUsers(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.role IN :roles AND u.lastActiveDate >= :from")
    long countRecentlyActiveUsersByRoles(@Param("from") LocalDateTime from,
                                           @Param("roles") java.util.Collection<UserRole> roles);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countNewUsersByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND role IN (:roles)
              AND created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countNewUsersByPeriodAndRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("roles") java.util.Collection<String> roles);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', last_active_date)
                    WHEN :bucket = 'month' THEN date_trunc('month', last_active_date)
                    WHEN :bucket = 'week' THEN date_trunc('week', last_active_date)
                    ELSE date_trunc('day', last_active_date)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND last_active_date IS NOT NULL
              AND last_active_date >= :from
              AND last_active_date < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countActiveUsersByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', last_active_date)
                    WHEN :bucket = 'month' THEN date_trunc('month', last_active_date)
                    WHEN :bucket = 'week' THEN date_trunc('week', last_active_date)
                    ELSE date_trunc('day', last_active_date)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND role IN (:roles)
              AND last_active_date IS NOT NULL
              AND last_active_date >= :from
              AND last_active_date < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countActiveUsersByPeriodAndRoles(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("roles") java.util.Collection<String> roles);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
            """, nativeQuery = true)
    DashboardTokenBalanceSummaryProjection getTokenBalanceSummary();

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            WHERE u.deleted_at IS NULL
              AND u.role IN (:roles)
            """, nativeQuery = true)
    DashboardTokenBalanceSummaryProjection getTokenBalanceSummaryByRoles(@Param("roles") java.util.Collection<String> roles);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL")
    long countAllUsersBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL AND (u.accountStatus IS NULL OR u.accountStatus <> com.historytalk.entity.enums.AccountStatus.DEACTIVATED)")
    long countActiveUsersBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL AND u.accountStatus = com.historytalk.entity.enums.AccountStatus.DEACTIVATED")
    long countInactiveUsersBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NOT NULL")
    long countDeletedUsersBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL AND u.role = :role")
    long countActiveUsersBySchoolIdAndRole(@Param("schoolId") UUID schoolId, @Param("role") UserRole role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL AND u.createdAt >= :from AND u.createdAt < :to")
    long countCreatedBetweenBySchoolId(@Param("schoolId") UUID schoolId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.deletedAt IS NULL AND u.lastActiveDate >= :from")
    long countRecentlyActiveUsersBySchoolId(@Param("schoolId") UUID schoolId,
                                            @Param("from") LocalDateTime from);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', created_at)
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND school_id = :schoolId
              AND created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countNewUsersByPeriodAndSchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'year' THEN date_trunc('year', last_active_date)
                    WHEN :bucket = 'month' THEN date_trunc('month', last_active_date)
                    WHEN :bucket = 'week' THEN date_trunc('week', last_active_date)
                    ELSE date_trunc('day', last_active_date)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND school_id = :schoolId
              AND last_active_date IS NOT NULL
              AND last_active_date >= :from
              AND last_active_date < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countActiveUsersByPeriodAndSchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            WHERE u.deleted_at IS NULL
              AND u.school_id = :schoolId
            """, nativeQuery = true)
    DashboardTokenBalanceSummaryProjection getTokenBalanceSummaryBySchoolId(@Param("schoolId") UUID schoolId);

    @Query(value = """
            WITH ranked_tiers AS (
                SELECT ut.uid, ut.tier_id,
                       ROW_NUMBER() OVER(PARTITION BY ut.uid ORDER BY t.amount DESC) as rn
                FROM user_tier ut
                JOIN tier t ON t.tier_id = ut.tier_id
                WHERE ut.deleted_at IS NULL
                  AND ut.is_active = true
                  AND ut.end_time > CURRENT_TIMESTAMP
                  AND t.deleted_at IS NULL
            )
            SELECT MIN(CAST(t.tier_id AS text)) AS "tierId",
                   LOWER(COALESCE(t.title, 'free')) AS "tierTitle",
                   COUNT(u.uid) AS users,
                   COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            LEFT JOIN ranked_tiers rt ON rt.uid = u.uid AND rt.rn = 1
            LEFT JOIN tier t ON (
                (rt.tier_id IS NOT NULL AND t.tier_id = rt.tier_id)
                OR
                (rt.tier_id IS NULL AND t.amount = 0)
            )
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND t.deleted_at IS NULL
            GROUP BY LOWER(COALESCE(t.title, 'free'))
            ORDER BY users DESC, "tierTitle" ASC
            """, nativeQuery = true)
    List<DashboardTokenBalanceByTierProjection> countTokenBalanceByTier();

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.deletedAt = null, u.accountStatus = com.historytalk.entity.enums.AccountStatus.ACTIVE, u.deactivatedReason = null WHERE u.deletedAt IS NOT NULL")
    int restoreAllUsers();
}
