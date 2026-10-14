package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for School Admin to approve / change status of local content.
 * Supports: DRAFT (revert), INACTIVE (hide), PUBLISHED (approve).
 * Uses ContentStatus enum — PUBLISHED maps to isPublished=true in the entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLocalContentStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    @JsonProperty("status")
    private ContentStatus status;

    /** Optional rejection / approval note for School Admin. */
    @JsonProperty("note")
    private String note;
}
