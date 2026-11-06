package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentScope;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request body for Teacher updating their own local character.
 * All fields are optional (partial update).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLocalCharacterRequest {

    @Size(min = 2, max = 100, message = "Tên nhân vật phải từ 2 đến 100 ký tự")
    @JsonProperty("name")
    private String name;

    @Size(max = 150, message = "Danh hiệu tối đa 150 ký tự")
    @JsonProperty("title")
    private String title;

    @JsonProperty("background")
    private String background;

    @JsonProperty("personality")
    private String personality;

    /** One of the voices from GET /api/v1/voices. Empty means the default voice. */
    @JsonProperty("voiceId")
    private String voiceId;

    /** How the character refers to itself, e.g. "tôi". Empty means "ta". */
    @Size(max = 30, message = "Cách xưng của nhân vật tối đa 30 ký tự")
    @JsonProperty("selfPronoun")
    private String selfPronoun;

    /** How the character addresses the learner, e.g. "bạn". Empty means "ngươi". */
    @Size(max = 30, message = "Cách gọi người học tối đa 30 ký tự")
    @JsonProperty("addressPronoun")
    private String addressPronoun;

    @Size(max = 500)
    @JsonProperty("imageUrl")
    private String imageUrl;

    @Size(max = 500)
    @JsonProperty("modelUrl")
    private String modelUrl;

    @JsonProperty("bornYear")
    private Integer bornYear;

    @JsonProperty("bornMonth")
    private Integer bornMonth;

    @JsonProperty("bornDay")
    private Integer bornDay;

    @JsonProperty("isBornBc")
    private Boolean isBornBc;

    @JsonProperty("deathYear")
    private Integer deathYear;

    @JsonProperty("deathMonth")
    private Integer deathMonth;

    @JsonProperty("deathDay")
    private Integer deathDay;

    @JsonProperty("isDeathBc")
    private Boolean isDeathBc;

    /**
     * Change scope. Changing scope resets PUBLISHED status back to DRAFT
     * and clears approved_by — must be re-approved by School Admin.
     */
    @JsonProperty("contentScope")
    private ContentScope contentScope;

    /** Replace linked context IDs. Pass null to keep existing; pass empty list to clear all. */
    @JsonProperty("contextIds")
    private List<String> contextIds;

    /** IDs of classrooms to assign this character to (replaces existing assignments). */
    @JsonProperty("classIds")
    private List<String> classIds;

    /** Publish or unpublish the local character. */
    @JsonProperty("isPublished")
    private Boolean isPublished;
}
