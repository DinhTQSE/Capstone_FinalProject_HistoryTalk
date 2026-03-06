package com.historyTalk.repository;

import com.historyTalk.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String userName);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserNameIgnoreCase(String userName);
}
