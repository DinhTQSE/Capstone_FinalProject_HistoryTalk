package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.CreateTierRequest;
import com.historytalk.dto.gamification.TierResponse;

import java.util.List;
import java.util.UUID;

public interface TierService {
    List<TierResponse> getAllTiers();
    TierResponse getTierById(String id);
    TierResponse createTier(CreateTierRequest request);
    TierResponse updateTier(String id, CreateTierRequest request);
    void deleteTier(String id);
}
