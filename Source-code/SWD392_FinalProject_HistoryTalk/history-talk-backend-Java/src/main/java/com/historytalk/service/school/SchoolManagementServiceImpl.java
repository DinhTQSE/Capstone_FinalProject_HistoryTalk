package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.SchoolTokenQuotaResponse;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolManagementServiceImpl implements SchoolManagementService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${historytalk.saas.package.small-tokens:5000000}")
    private int packageSmallTokens;

    @Value("${historytalk.saas.package.medium-tokens:20000000}")
    private int packageMediumTokens;

    @Value("${historytalk.saas.package.large-tokens:50000000}")
    private int packageLargeTokens;

    private static final Pattern SCHOOL_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,30}$");
    private static final Set<String> STOP_WORDS = Set.of(
            "TRUONG", "THPT", "THCS", "TIEU", "HOC", "DAI", "PHO", "THONG", "LIEN", "CAP", "CHUYEN"
    );

    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("HO CHI MINH", "HCM"),
            Map.entry("TP HO CHI MINH", "HCM"),
            Map.entry("TPHCM", "HCM"),
            Map.entry("SAI GON", "HCM"),
            Map.entry("HA NOI", "HN"),
            Map.entry("DA NANG", "DN"),
            Map.entry("HAI PHONG", "HP"),
            Map.entry("CAN THO", "CT"),
            Map.entry("HUE", "HUE"),
            Map.entry("THUA THIEN HUE", "HUE"),
            Map.entry("BA RIA VUNG TAU", "BRVT"),
            Map.entry("VUNG TAU", "BRVT"),
            Map.entry("BINH DUONG", "BD"),
            Map.entry("DONG NAI", "DNA"),
            Map.entry("NAM DINH", "ND"),
            Map.entry("BAC NINH", "BN"),
            Map.entry("BAC GIANG", "BG"),
            Map.entry("HAI DUONG", "HD"),
            Map.entry("HUNG YEN", "HY"),
            Map.entry("THAI BINH", "TB"),
            Map.entry("HA NAM", "HNA"),
            Map.entry("NINH BINH", "NB"),
            Map.entry("THANH HOA", "TH"),
            Map.entry("NGHE AN", "NA"),
            Map.entry("HA TINH", "HT"),
            Map.entry("QUANG BINH", "QB"),
            Map.entry("QUANG TRI", "QT"),
            Map.entry("QUANG NAM", "QNM"),
            Map.entry("QUANG NGAI", "QNG"),
            Map.entry("BINH DINH", "BDH"),
            Map.entry("PHU YEN", "PY"),
            Map.entry("KHANH HOA", "KH"),
            Map.entry("NINH THUAN", "NT"),
            Map.entry("BINH THUAN", "BTH"),
            Map.entry("KON TUM", "KT"),
            Map.entry("GIA LAI", "GL"),
            Map.entry("DAK LAK", "DLK"),
            Map.entry("DAK NONG", "DKN"),
            Map.entry("LAM DONG", "LD"),
            Map.entry("BINH PHUOC", "BP"),
            Map.entry("TAY NINH", "TN"),
            Map.entry("LONG AN", "LA"),
            Map.entry("TIEN GIANG", "TG"),
            Map.entry("BEN TRE", "BT"),
            Map.entry("TRA VINH", "TV"),
            Map.entry("VINH LONG", "VL"),
            Map.entry("DONG THAP", "DT"),
            Map.entry("AN GIANG", "AG"),
            Map.entry("KIEN GIANG", "KG"),
            Map.entry("HAU GIANG", "HG"),
            Map.entry("SOC TRANG", "ST"),
            Map.entry("BAC LIEU", "BL"),
            Map.entry("CA MAU", "CM")
    );

    private static final Set<String> LOCATION_STOP_WORDS = Set.of(
            "TP", "THANH", "PHO", "TINH", "QUAN", "HUYEN", "THI", "XA", "PHUONG", "VIETNAM", "VN"
    );

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public SchoolResponse createSchool(CreateSchoolRequest request) {
        String finalSchoolCode = resolveOrGenerateSchoolCode(request.getSchoolCode(), request.getName(), request.getAddress());

        if (Boolean.FALSE.equals(request.getLocalHistoryPolicyAccepted())) {
            throw new InvalidRequestException("School must agree to the local history content terms and conditions");
        }

        int totalQuota = resolvePackageTokens(request.getPackageType());

        School school = School.builder()
                .name(request.getName().trim())
                .schoolCode(finalSchoolCode)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .contactPhone(request.getContactPhone() != null ? request.getContactPhone().trim() : null)
                .packageType(request.getPackageType())
                .totalSchoolTokenQuota(totalQuota)
                .unallocatedTokenQuota(totalQuota)
                .localHistoryPolicyAccepted(true)
                .build();

        School savedSchool = schoolRepository.save(school);
        log.info("Created new school: {} ({}) with quota: {} tokens", savedSchool.getName(), savedSchool.getSchoolCode(), totalQuota);

        return mapToSchoolResponse(savedSchool);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolResponse getSchoolById(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));
        return mapToSchoolResponse(school);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SchoolResponse> getSchools(String search, Pageable pageable) {
        Page<School> schoolPage;
        if (search != null && !search.trim().isEmpty()) {
            String keyword = search.trim();
            schoolPage = schoolRepository.findByNameContainingIgnoreCaseOrSchoolCodeContainingIgnoreCaseOrContactEmailContainingIgnoreCase(
                    keyword, keyword, keyword, pageable);
        } else {
            schoolPage = schoolRepository.findAll(pageable);
        }

        List<SchoolResponse> content = schoolPage.getContent().stream()
                .map(this::mapToSchoolResponse)
                .toList();

        return PaginatedResponse.<SchoolResponse>builder()
                .content(content)
                .totalElements(schoolPage.getTotalElements())
                .totalPages(schoolPage.getTotalPages())
                .currentPage(schoolPage.getNumber())
                .pageSize(schoolPage.getSize())
                .hasNext(schoolPage.hasNext())
                .hasPrevious(schoolPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public SchoolAdminResponse createSchoolAdmin(CreateSchoolAdminRequest request) {
        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + request.getSchoolId()));

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DataConflictException("Email " + normalizedEmail + " is already in use by another account");
        }

        String baseUserName = school.getSchoolCode().toLowerCase() + "_admin";
        String userName = baseUserName;
        int counter = 1;
        while (userRepository.existsByUserNameIgnoreCase(userName)) {
            userName = baseUserName + "_" + counter++;
        }

        String rawPassword = generateRandomPassword(10);

        User schoolAdmin = User.builder()
                .userName(userName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .mustChangePassword(true)
                .token(0)
                .build();

        User savedUser = userRepository.save(schoolAdmin);
        log.info("Created School Admin account: {} for school: {}", savedUser.getUserName(), school.getName());

        return SchoolAdminResponse.builder()
                .uid(savedUser.getUid())
                .userName(savedUser.getUserName())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .phoneNumber(savedUser.getPhoneNumber())
                .role(savedUser.getRole())
                .schoolId(school.getId())
                .schoolName(school.getName())
                .initialPassword(rawPassword)
                .createdAt(savedUser.getCreatedAt() != null ? savedUser.getCreatedAt() : java.time.LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolTokenQuotaResponse getSchoolTokenQuota(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));

        long total = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0L;
        long unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0L;
        long allocated = Math.max(0, total - unallocated);
        double usagePercent = total > 0 ? (double) allocated / total * 100.0 : 0.0;

        return SchoolTokenQuotaResponse.builder()
                .schoolId(school.getId())
                .schoolName(school.getName())
                .schoolCode(school.getSchoolCode())
                .packageType(school.getPackageType())
                .totalSchoolTokenQuota(total)
                .unallocatedTokenQuota(unallocated)
                .allocatedTokenQuota(allocated)
                .usagePercentage(Math.round(usagePercent * 100.0) / 100.0)
                .build();
    }

    private SchoolResponse mapToSchoolResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .name(school.getName())
                .schoolCode(school.getSchoolCode())
                .address(school.getAddress())
                .contactEmail(school.getContactEmail())
                .contactPhone(school.getContactPhone())
                .packageType(school.getPackageType())
                .totalSchoolTokenQuota(school.getTotalSchoolTokenQuota())
                .unallocatedTokenQuota(school.getUnallocatedTokenQuota())
                .localHistoryPolicyAccepted(school.getLocalHistoryPolicyAccepted())
                .status(school.getStatus())
                .createdAt(school.getCreatedAt() != null ? school.getCreatedAt() : java.time.LocalDateTime.now())
                .build();
    }

    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder("Ht@");
        for (int i = 3; i < length; i++) {
            sb.append(CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private String resolveOrGenerateSchoolCode(String schoolCodeInput, String schoolName, String address) {
        if (schoolCodeInput != null && !schoolCodeInput.trim().isEmpty()) {
            String customCode = schoolCodeInput.trim().toUpperCase();
            if (!SCHOOL_CODE_PATTERN.matcher(customCode).matches()) {
                throw new InvalidRequestException("School code must contain 2-30 alphanumeric characters, hyphens or underscores");
            }
            if (schoolRepository.existsBySchoolCodeIgnoreCase(customCode)) {
                throw new DataConflictException("School code " + customCode + " already exists in the system");
            }
            return customCode;
        }

        String nameAcronym = generateAcronym(schoolName);
        if (nameAcronym.isEmpty()) {
            nameAcronym = "SCH";
        }

        String locationAcronym = extractLocationAcronym(address);
        String baseCode = locationAcronym.isEmpty() ? nameAcronym : nameAcronym + "-" + locationAcronym;

        String candidate = baseCode;
        int sequence = 1;
        while (schoolRepository.existsBySchoolCodeIgnoreCase(candidate)) {
            candidate = String.format("%s-%02d", baseCode, sequence++);
        }
        return candidate;
    }

    private String extractLocationAcronym(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(address, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d").replace("Đ", "D")
                .toUpperCase();

        String targetSegment = normalized;
        if (normalized.contains(",")) {
            String[] segments = normalized.split(",");
            for (int i = segments.length - 1; i >= 0; i--) {
                String seg = segments[i].trim();
                if (!seg.isEmpty()) {
                    targetSegment = seg;
                    break;
                }
            }
        }

        String cleaned = targetSegment.replaceAll("[^A-Z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        for (Map.Entry<String, String> entry : PROVINCE_ALIASES.entrySet()) {
            if (cleaned.equals(entry.getKey()) || cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : PROVINCE_ALIASES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String[] tokens = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (!token.isEmpty() && !LOCATION_STOP_WORDS.contains(token)) {
                sb.append(token.charAt(0));
            }
        }

        String acronym = sb.toString();
        if (acronym.length() < 2) {
            String compact = cleaned.replaceAll("\\s+", "");
            if (compact.length() >= 2) {
                acronym = compact.substring(0, Math.min(compact.length(), 3));
            }
        }

        return acronym.length() > 6 ? acronym.substring(0, 6) : acronym;
    }

    private String generateAcronym(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "SCH";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d").replace("Đ", "D");

        String[] tokens = normalized.toUpperCase().split("[^A-Z0-9]+");
        StringBuilder acronymBuilder = new StringBuilder();

        for (String token : tokens) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) {
                continue;
            }
            acronymBuilder.append(token.charAt(0));
        }

        String acronym = acronymBuilder.toString();
        if (acronym.length() < 2) {
            acronymBuilder = new StringBuilder();
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    acronymBuilder.append(token.charAt(0));
                }
            }
            acronym = acronymBuilder.toString();
        }

        if (acronym.length() < 2) {
            acronym = (acronym + "SCH").substring(0, 3);
        }

        if (acronym.length() > 15) {
            acronym = acronym.substring(0, 15);
        }

        return acronym;
    }

    private int resolvePackageTokens(EnterprisePackage packageType) {
        if (packageType == null) {
            return 0;
        }
        return switch (packageType) {
            case ENTERPRISE_SMALL -> packageSmallTokens;
            case ENTERPRISE_MEDIUM -> packageMediumTokens;
            case ENTERPRISE_LARGE -> packageLargeTokens;
        };
    }
}
