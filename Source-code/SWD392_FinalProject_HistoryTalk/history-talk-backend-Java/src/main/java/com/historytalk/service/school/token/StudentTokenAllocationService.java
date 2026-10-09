package com.historytalk.service.school.token;

import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;

import java.util.UUID;

public interface StudentTokenAllocationService {

    AllocateStudentTokenResponse allocateTokens(UUID schoolId, AllocateStudentTokenRequest request);
}
