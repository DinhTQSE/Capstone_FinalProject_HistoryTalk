package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Response DTO for local content (Context or Character).
 * Carries extra local-content metadata on top of standard fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalContentResponse {

    // ── Common ──────────────────────────────────────────────────────────────
    @JsonProperty("id")
    private String id;          // contextId or characterId

    @JsonProperty("type")
    private String type;        // "CONTEXT" | "CHARACTER"

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;  // context description / character background

    @JsonProperty("imageUrl")
    private String imageUrl;

    // ── Status ───────────────────────────────────────────────────────────────
    @JsonProperty("status")
    private ContentStatus status;

    @JsonProperty("isPublished")
    private Boolean isPublished;

    @JsonProperty("contentScope")
    private ContentScope contentScope;

    // ── Context-specific ─────────────────────────────────────────────────────
    @JsonProperty("era")
    private EventEra era;

    @JsonProperty("category")
    private EventCategory category;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("startYear")
    private Integer startYear;

    @JsonProperty("endYear")
    private Integer endYear;

    @JsonProperty("isBC")
    private Boolean isBC;

    @JsonProperty("location")
    private String location;

    @JsonProperty("videoUrl")
    private String videoUrl;

    // ── Character-specific ───────────────────────────────────────────────────
    @JsonProperty("title")
    private String title;

    @JsonProperty("personality")
    private String personality;

    @JsonProperty("modelUrl")
    private String modelUrl;

    // ── Audit ────────────────────────────────────────────────────────────────
    @JsonProperty("createdBy")
    private AuthorInfo createdBy;

    @JsonProperty("approvedBy")
    private AuthorInfo approvedBy;

    @JsonProperty("schoolId")
    private String schoolId;

    @JsonProperty("schoolName")
    private String schoolName;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuthorInfo {
        @JsonProperty("uid")
        private String uid;

        @JsonProperty("userName")
        private String userName;

        @JsonProperty("fullName")
        private String fullName;
    }
}
