package com.historyTalk.repository;

import com.historyTalk.entity.staff.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, String> {
    Optional<Staff> findByEmailIgnoreCase(String email);
}
