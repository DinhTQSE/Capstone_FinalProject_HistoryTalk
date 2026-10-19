package com.historytalk.service.school;

import com.historytalk.dto.school.AcademicYearRolloverRequest;
import com.historytalk.dto.school.AcademicYearRolloverResponse;
import com.historytalk.dto.school.DeactivateStudentRequest;
import com.historytalk.dto.school.RenewSchoolSubscriptionRequest;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.TopUpSchoolTokensRequest;
import com.historytalk.dto.school.UpdateSchoolTokenPolicyRequest;
import com.historytalk.security.UserPrincipal;

import java.time.LocalDate;
import java.util.UUID;

public interface AcademicLifecycleService {

    SchoolResponse renewSchoolSubscription(UUID schoolId, RenewSchoolSubscriptionRequest request);

    SchoolResponse activateRenewedSubscription(UUID schoolId, LocalDate referenceDate);

    SchoolResponse topUpSchoolTokens(UUID schoolId, TopUpSchoolTokensRequest request);

    SchoolResponse updateSchoolTokenPolicy(UserPrincipal schoolAdminPrincipal, UpdateSchoolTokenPolicyRequest request);

    AcademicYearRolloverResponse rolloverAcademicYear(
            UserPrincipal schoolAdminPrincipal, AcademicYearRolloverRequest request);

    StudentResponse deactivateStudentAccount(
            UserPrincipal schoolAdminPrincipal, UUID studentUid, DeactivateStudentRequest request);
}
