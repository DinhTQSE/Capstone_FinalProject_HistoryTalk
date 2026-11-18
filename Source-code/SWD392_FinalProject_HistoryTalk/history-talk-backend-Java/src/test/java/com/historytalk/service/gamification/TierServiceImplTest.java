package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.CreateTierRequest;
import com.historytalk.dto.gamification.TierResponse;
import com.historytalk.entity.payment.Tier;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.payment.TierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierServiceImplTest {

    @Mock
    private TierRepository tierRepository;

    @InjectMocks
    private TierServiceImpl tierService;

    @Test
    @DisplayName("WF-05 / F-10: seedDefaultTiers seeds 5 default subscription tiers when empty")
    void seedDefaultTiers_WhenEmpty_SeedsFiveTiers() {
        when(tierRepository.count()).thenReturn(0L);

        tierService.seedDefaultTiers();

        verify(tierRepository).saveAll(argThat((List<Tier> list) -> list.size() == 5));
    }

    @Test
    @DisplayName("WF-05 / F-10: getAllTiers and getTierById return mapped tier responses")
    void getAllTiersAndGetById_Success() {
        UUID tierId = UUID.randomUUID();
        Tier tier = Tier.builder()
                .tierId(tierId)
                .title("plus")
                .amount(49000)
                .noMonth(1)
                .limitedToken(5000)
                .isActive(true)
                .build();

        when(tierRepository.findAll()).thenReturn(List.of(tier));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        List<TierResponse> all = tierService.getAllTiers();
        TierResponse single = tierService.getTierById(tierId.toString());

        assertThat(all).hasSize(1);
        assertThat(single.getTitle()).isEqualTo("plus");
        assertThat(single.getAmount()).isEqualTo(49000.0);
    }

    @Test
    @DisplayName("WF-05 / F-10: createTier, updateTier, and deleteTier manage subscription catalog")
    void createUpdateAndDeleteTier_Success() {
        UUID tierId = UUID.randomUUID();
        CreateTierRequest createReq = new CreateTierRequest("pro", 99000.0, 1, 10000, true);

        when(tierRepository.save(any(Tier.class))).thenAnswer(inv -> {
            Tier t = inv.getArgument(0);
            if (t.getTierId() == null) {
                t.setTierId(tierId);
            }
            return t;
        });

        TierResponse created = tierService.createTier(createReq);
        assertThat(created.getTitle()).isEqualTo("pro");
        assertThat(created.getAmount()).isEqualTo(99000.0);

        Tier existing = Tier.builder()
                .tierId(tierId)
                .title("pro")
                .amount(99000)
                .noMonth(1)
                .limitedToken(10000)
                .isActive(true)
                .build();
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));

        CreateTierRequest updateReq = new CreateTierRequest("pro-yearly", 999000.0, 12, 12000, false);
        TierResponse updated = tierService.updateTier(tierId.toString(), updateReq);
        assertThat(updated.getTitle()).isEqualTo("pro-yearly");
        assertThat(updated.getNoMonth()).isEqualTo(12);
        assertThat(updated.getIsActive()).isFalse();

        tierService.deleteTier(tierId.toString());
        verify(tierRepository).delete(existing);
    }

    @Test
    @DisplayName("WF-05: getTierById throws ResourceNotFoundException when tier does not exist")
    void getTierById_NotFound_ThrowsException() {
        UUID tierId = UUID.randomUUID();
        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tierService.getTierById(tierId.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
