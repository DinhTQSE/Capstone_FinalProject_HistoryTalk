package com.historytalk.repository.school;

import com.historytalk.entity.school.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findBySchoolCodeIgnoreCase(String schoolCode);

    boolean existsBySchoolCodeIgnoreCase(String schoolCode);
}
