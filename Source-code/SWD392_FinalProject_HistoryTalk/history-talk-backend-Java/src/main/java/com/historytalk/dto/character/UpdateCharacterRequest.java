package com.historytalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCharacterRequest {

    @JsonProperty("name")
    @Size(min = 2, max = 100, message = "Tên nhân vật phải từ 2 đến 100 ký tự")
    private String name;

    @JsonProperty("title")
    @Size(max = 150, message = "Tiêu đề không được vượt quá 150 ký tự")
    private String title;

    @JsonProperty("background")
    private String background;

    @JsonProperty("image")
    @Size(max = 255, message = "URL hình ảnh không được vượt quá 255 ký tự")
    private String imageUrl;

    @JsonProperty("modelUrl")
    @Size(max = 500, message = "URL mô hình 3D không được vượt quá 500 ký tự")
    private String modelUrl;

    @JsonProperty("personality")
    @Size(max = 500, message = "Tính cách không được vượt quá 500 ký tự")
    private String personality;

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

    @JsonProperty("isPublished")
    private Boolean isPublished;
}
