package com.historytalk.repository.school;

import com.historytalk.entity.school.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findBySchoolCodeIgnoreCase(String schoolCode);

    boolean existsBySchoolCodeIgnoreCase(String schoolCode);

    Page<School> findByNameContainingIgnoreCaseOrSchoolCodeContainingIgnoreCaseOrContactEmailContainingIgnoreCase(
            String name, String schoolCode, String contactEmail, Pageable pageable
    );

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(s.totalSchoolTokenQuota), 0) FROM School s WHERE s.deletedAt IS NULL")
    long sumTotalSchoolTokenQuota();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(s.unallocatedTokenQuota), 0) FROM School s WHERE s.deletedAt IS NULL")
    long sumUnallocatedSchoolTokenQuota();

    long countByStatusAndDeletedAtIsNull(com.historytalk.entity.enums.SchoolStatus status);
}
