package com.historytalk.service.historicalContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.HistoricalContextResponse;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalContextServiceImplTest {

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private HistoricalContextServiceImpl historicalContextService;

    @Test
    void getAllContextsExcludesDeletedRecordsForStaff() {
        HistoricalContext context = activeContext();
        Pageable pageable = PageRequest.of(0, 10);

        when(contextRepository.findAllWithSearch(eq("dynasty"), any(), any(), eq(true), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(context), pageable, 1));

        PaginatedResponse<HistoricalContextResponse> response =
                historicalContextService.getAllContexts("dynasty", null, null, pageable, "CONTENT_ADMIN");

        assertThat(response.getContent()).hasSize(1);
        ArgumentCaptor<Boolean> includeDeletedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(contextRepository).findAllWithSearch(eq("dynasty"), any(), any(), eq(true), includeDeletedCaptor.capture(), eq(pageable));
        assertThat(includeDeletedCaptor.getValue()).isFalse();
    }

    @Test
    void getAllContextsSimpleExcludesDeletedRecordsForStaff() {
        when(contextRepository.findAllSimple(eq("dynasty"), eq(true), eq(false)))
                .thenReturn(List.of(activeContext()));

        List<HistoricalContextResponse> response =
                historicalContextService.getAllContextsSimple("dynasty", "CONTENT_ADMIN");

        assertThat(response).hasSize(1);
        ArgumentCaptor<Boolean> includeDeletedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(contextRepository).findAllSimple(eq("dynasty"), eq(true), includeDeletedCaptor.capture());
        assertThat(includeDeletedCaptor.getValue()).isFalse();
    }

    @Test
    void historicalContextResponseJsonDoesNotExposeDeletedAt() throws Exception {
        HistoricalContextResponse response = HistoricalContextResponse.builder()
                .contextId(UUID.randomUUID().toString())
                .name("Tran Dynasty")
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("deletedAt");
    }

    private HistoricalContext activeContext() {
        User creator = User.builder()
                .uid(UUID.randomUUID())
                .userName("staff")
                .build();
        return HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Tran Dynasty")
                .description("Historical context description")
                .isPublished(true)
                .createdBy(creator)
                .build();
    }
}
