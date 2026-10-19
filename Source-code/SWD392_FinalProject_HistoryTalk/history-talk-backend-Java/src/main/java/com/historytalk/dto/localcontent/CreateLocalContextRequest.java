package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request body for Teacher creating a Local Historical Context.
 * school_id and createdBy are derived from the JWT principal — not accepted from the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLocalContextRequest {

    @NotBlank(message = "Tên bối cảnh không được để trống")
    @Size(min = 3, max = 100, message = "Tên bối cảnh phải từ 3 đến 100 ký tự")
    @JsonProperty("name")
    private String name;

    @NotBlank(message = "Mô tả bối cảnh không được để trống")
    @Size(min = 10, max = 5000, message = "Mô tả bối cảnh phải từ 10 đến 5000 ký tự")
    @JsonProperty("description")
    private String description;

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
    private Boolean isBC = false;

    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    @JsonProperty("location")
    private String location;

    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    @JsonProperty("imageUrl")
    private String imageUrl;

    @Size(max = 500, message = "URL video tối đa 500 ký tự")
    @JsonProperty("videoUrl")
    private String videoUrl;

    /**
     * CLASS_ONLY (default) – chỉ lớp mình thấy.
     * SCHOOL_WIDE – chia sẻ kho nội dung toàn trường (giáo viên khác có thể kéo về lớp).
     */
    @Builder.Default
    @JsonProperty("contentScope")
    private ContentScope contentScope = ContentScope.CLASS_ONLY;

    /** Latitude for map pin (optional). */
    @JsonProperty("latitude")
    private Double latitude;

    /** Longitude for map pin (optional). */
    @JsonProperty("longitude")
    private Double longitude;

    /** IDs of classrooms to assign this context to on creation. */
    @JsonProperty("classIds")
    private List<String> classIds;

    /**
     * Whether the local context is published immediately upon creation.
     * Defaults to true (teachers can self-publish without admin approval).
     */
    @Builder.Default
    @JsonProperty("isPublished")
    private Boolean isPublished = true;
}
