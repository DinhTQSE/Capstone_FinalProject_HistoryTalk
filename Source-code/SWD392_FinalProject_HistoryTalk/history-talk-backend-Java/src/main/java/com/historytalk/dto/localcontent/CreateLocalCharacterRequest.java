package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentScope;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for Teacher creating a Local Character.
 * Mirrors the global CreateCharacterRequest but adds contentScope.
 * school_id and createdBy are resolved from JWT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLocalCharacterRequest {

    @jakarta.validation.constraints.NotBlank(message = "Tên nhân vật không được để trống")
    @Size(min = 2, max = 100, message = "Tên nhân vật phải từ 2 đến 100 ký tự")
    @JsonProperty("name")
    private String name;

    @Size(max = 150, message = "Danh hiệu tối đa 150 ký tự")
    @JsonProperty("title")
    private String title;

    @jakarta.validation.constraints.NotBlank(message = "Tiểu sử nhân vật không được để trống")
    @JsonProperty("background")
    private String background;

    @JsonProperty("personality")
    private String personality;

    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    @JsonProperty("imageUrl")
    private String imageUrl;

    @Size(max = 500, message = "URL mô hình 3D tối đa 500 ký tự")
    @JsonProperty("modelUrl")
    private String modelUrl;

    @JsonProperty("bornYear")
    private Integer bornYear;

    @JsonProperty("bornMonth")
    private Integer bornMonth;

    @JsonProperty("bornDay")
    private Integer bornDay;

    @JsonProperty("isBornBc")
    private Boolean isBornBc = false;

    @JsonProperty("deathYear")
    private Integer deathYear;

    @JsonProperty("deathMonth")
    private Integer deathMonth;

    @JsonProperty("deathDay")
    private Integer deathDay;

    @JsonProperty("isDeathBc")
    private Boolean isDeathBc = false;

    /**
     * Optional: link local character to one or more local (or global) contexts.
     * List of contextId strings.
     */
    @JsonProperty("contextIds")
    private java.util.List<String> contextIds;

    /**
     * CLASS_ONLY (default) – chỉ lớp mình thấy.
     * SCHOOL_WIDE – chia sẻ kho nội dung toàn trường.
     */
    @Builder.Default
    @JsonProperty("contentScope")
    private ContentScope contentScope = ContentScope.CLASS_ONLY;

    /** IDs of classrooms to assign this character to on creation. */
    @JsonProperty("classIds")
    private java.util.List<String> classIds;

    /**
     * Whether the local character is published immediately upon creation.
     * Defaults to true (teachers can self-publish without admin approval).
     */
    @Builder.Default
    @JsonProperty("isPublished")
    private Boolean isPublished = true;
}
