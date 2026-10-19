package com.historytalk.controller.localcontent;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.localcontent.LocalContentResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Student endpoint to browse published local content from their school.
 *
 * <p><b>Access:</b> SCHOOL_STUDENT only.
 * <p><b>Rule (UC-14 from Role Matrix):</b>
 * Students see published local content that belongs to their school.
 * The actual per-class filtering is enforced at the Assignment layer
 * (teacher assigns specific content to a specific class).
 */
@RestController
@RequestMapping("/api/v1/student/local-contents")
@PreAuthorize("hasRole('SCHOOL_STUDENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Local Content — Student", description = "Student: view published local history content from their school (UC-14)")
public class StudentLocalContentController {

    private final LocalContextRepository localContextRepository;
    private final LocalCharacterRepository localCharacterRepository;
    private final UserRepository userRepository;

    @GetMapping("/contexts")
    @Operation(
            summary = "Xem danh sách bối cảnh lịch sử địa phương đã duyệt của trường",
            description = "Học sinh xem được bối cảnh lịch sử địa phương trường mình đã được School Admin duyệt (PUBLISHED). " +
                    "Phân quyền chi tiết theo lớp được xử lý ở tầng Assignment.")
    public ResponseEntity<ApiResponse<List<LocalContentResponse>>> getPublishedLocalContexts(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        List<HistoricalContext> contexts = localContextRepository.findPublishedBySchool(schoolId);
        List<LocalContentResponse> response = contexts.stream()
                .map(this::mapContext)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response,
                "Published local contexts retrieved successfully — " + response.size() + " found"));
    }

    @GetMapping("/characters")
    @Operation(
            summary = "Xem danh sách nhân vật lịch sử địa phương đã duyệt của trường",
            description = "Học sinh xem được nhân vật lịch sử địa phương trường mình đã được School Admin duyệt (PUBLISHED).")
    public ResponseEntity<ApiResponse<List<LocalContentResponse>>> getPublishedLocalCharacters(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        List<Character> characters = localCharacterRepository.findPublishedBySchool(schoolId);
        List<LocalContentResponse> response = characters.stream()
                .map(this::mapCharacter)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response,
                "Published local characters retrieved successfully — " + response.size() + " found"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveSchoolId(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal.getSchoolId();
        }
        String userId = (principal != null) ? principal.getUid() : SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập với tài khoản Học sinh");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản học sinh"));
        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản học sinh chưa được liên kết với trường học");
        }
        return user.getSchool().getId();
    }

    private LocalContentResponse mapContext(HistoricalContext ctx) {
        return LocalContentResponse.builder()
                .id(ctx.getContextId().toString())
                .type("CONTEXT")
                .name(ctx.getName())
                .description(ctx.getDescription())
                .imageUrl(ctx.getImageUrl())
                .isPublished(ctx.getIsPublished())
                .contentScope(ctx.getContentScope())
                .era(ctx.getEra())
                .category(ctx.getCategory())
                .year(ctx.getYear())
                .startYear(ctx.getStartYear())
                .endYear(ctx.getEndYear())
                .isBC(ctx.getIsBC())
                .location(ctx.getLocation())
                .videoUrl(ctx.getVideoUrl())
                .schoolId(ctx.getSchool() != null ? ctx.getSchool().getId().toString() : null)
                .schoolName(ctx.getSchool() != null ? ctx.getSchool().getName() : null)
                .createdAt(ctx.getCreatedAt())
                .build();
    }

    private LocalContentResponse mapCharacter(Character c) {
        return LocalContentResponse.builder()
                .id(c.getCharacterId().toString())
                .type("CHARACTER")
                .name(c.getName())
                .description(c.getBackground())
                .imageUrl(c.getImageUrl())
                .isPublished(c.getIsPublished())
                .contentScope(c.getContentScope())
                .title(c.getTitle())
                .personality(c.getPersonality())
                .modelUrl(c.getModelUrl())
                .schoolId(c.getSchool() != null ? c.getSchool().getId().toString() : null)
                .schoolName(c.getSchool() != null ? c.getSchool().getName() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
