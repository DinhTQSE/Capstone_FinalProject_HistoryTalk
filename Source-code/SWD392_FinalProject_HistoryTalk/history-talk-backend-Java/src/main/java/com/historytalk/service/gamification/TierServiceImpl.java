package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.CreateTierRequest;
import com.historytalk.dto.gamification.TierResponse;
import com.historytalk.entity.gamification.Tier;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.gamification.TierRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TierServiceImpl implements TierService {

    private final TierRepository tierRepository;

    @PostConstruct
    public void seedDefaultTiers() {
        try {
            if (tierRepository.count() == 0) {
                List<Tier> defaultTiers = List.of(
                        Tier.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .title("free")
                                .amount(0.0)
                                .noMonth(1)
                                .limitedToken(20)
                                .isActive(true)
                                .build(),
                        Tier.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                                .title("plus")
                                .amount(49000.0)
                                .noMonth(1)
                                .limitedToken(100)
                                .isActive(true)
                                .build(),
                        Tier.builder()
                                .id(UUID.fromString("00000000-0000-0000-0000-000000000003"))
                                .title("pro")
                                .amount(99000.0)
                                .noMonth(1)
                                .limitedToken(999)
                                .isActive(true)
                                .build()
                );
                tierRepository.saveAll(defaultTiers);
                log.info("Default tiers (free, plus, pro) seeded successfully");
            }
        } catch (Exception e) {
            log.warn("Failed to seed default tiers: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TierResponse> getAllTiers() {
        return tierRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TierResponse getTierById(String id) {
        Tier tier = tierRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói hội viên: " + id));
        return mapToResponse(tier);
    }

    @Override
    @Transactional
    public TierResponse createTier(CreateTierRequest request) {
        Tier tier = Tier.builder()
                .title(request.getTitle())
                .amount(request.getAmount())
                .noMonth(request.getNoMonth())
                .limitedToken(request.getLimitedToken())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        Tier saved = tierRepository.save(tier);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public TierResponse updateTier(String id, CreateTierRequest request) {
        Tier tier = tierRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói hội viên: " + id));
        if (request.getTitle() != null && !request.getTitle().isBlank()) tier.setTitle(request.getTitle());
        if (request.getAmount() != null) tier.setAmount(request.getAmount());
        if (request.getNoMonth() != null) tier.setNoMonth(request.getNoMonth());
        if (request.getLimitedToken() != null) tier.setLimitedToken(request.getLimitedToken());
        if (request.getIsActive() != null) tier.setIsActive(request.getIsActive());
        Tier saved = tierRepository.save(tier);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTier(String id) {
        Tier tier = tierRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói hội viên: " + id));
        tierRepository.delete(tier);
    }

    private TierResponse mapToResponse(Tier t) {
        return TierResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .amount(t.getAmount())
                .noMonth(t.getNoMonth())
                .limitedToken(t.getLimitedToken())
                .isActive(t.getIsActive())
                .build();
    }
}
