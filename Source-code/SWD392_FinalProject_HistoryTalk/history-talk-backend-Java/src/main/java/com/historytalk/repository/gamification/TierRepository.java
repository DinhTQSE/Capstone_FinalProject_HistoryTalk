package com.historytalk.repository.gamification;

import com.historytalk.entity.gamification.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TierRepository extends JpaRepository<Tier, UUID> {
    List<Tier> findByIsActiveTrue();
}
