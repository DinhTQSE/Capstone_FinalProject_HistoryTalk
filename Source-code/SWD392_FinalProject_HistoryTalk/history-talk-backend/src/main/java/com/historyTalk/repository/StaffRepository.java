package com.historyTalk.repository;

import com.historyTalk.entity.staff.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByEmailIgnoreCase(String email);
}
