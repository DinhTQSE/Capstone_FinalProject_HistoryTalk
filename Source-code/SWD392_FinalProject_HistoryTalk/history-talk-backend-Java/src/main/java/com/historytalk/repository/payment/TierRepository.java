package com.historytalk.repository.payment;

import com.historytalk.entity.payment.Tier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
public interface TierRepository extends JpaRepository<Tier, UUID> {
    List<Tier> findByIsActiveTrueAndDeletedAtIsNull();
}
