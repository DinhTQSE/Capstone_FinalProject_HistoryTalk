package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.quote.ApproveB2bQuoteResponse;
import com.historytalk.dto.school.quote.B2bPlanCatalogResponse;
import com.historytalk.dto.school.quote.B2bQuoteCalculationResponse;
import com.historytalk.dto.school.quote.B2bQuoteResponse;
import com.historytalk.dto.school.quote.CalculateB2bQuoteRequest;
import com.historytalk.dto.school.quote.SubmitB2bQuoteRequest;
import com.historytalk.dto.school.quote.UpdateB2bQuoteStatusRequest;
import com.historytalk.entity.enums.B2bQuoteStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface B2bQuoteService {

    B2bPlanCatalogResponse getB2bPlanCatalog();

    B2bQuoteCalculationResponse calculateQuote(CalculateB2bQuoteRequest request);

    B2bQuoteResponse submitQuoteRequest(SubmitB2bQuoteRequest request);

    PaginatedResponse<B2bQuoteResponse> getQuoteRequests(B2bQuoteStatus status, String search, Pageable pageable);

    B2bQuoteResponse getQuoteRequestById(UUID quoteId);

    B2bQuoteResponse updateQuoteStatus(UUID quoteId, UpdateB2bQuoteStatusRequest request, UUID adminUid);

    ApproveB2bQuoteResponse approveQuoteAndProvisionSchool(UUID quoteId, UUID adminUid);
}
