package com.historytalk.dto.school.quote;

import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveB2bQuoteResponse {

    private B2bQuoteResponse quote;
    private SchoolResponse school;
    private SchoolAdminResponse schoolAdmin;
}
