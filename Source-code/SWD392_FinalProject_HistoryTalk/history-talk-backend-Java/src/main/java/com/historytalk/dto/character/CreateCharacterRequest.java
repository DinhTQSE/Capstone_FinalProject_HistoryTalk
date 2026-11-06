package com.historytalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCharacterRequest {

    @JsonProperty("name")
    @NotBlank(message = "Yêu cầu tên nhân vật")
    @Size(min = 2, max = 100, message = "Tên nhân vật phải từ 2 đến 100 ký tự")
    private String name;

    @JsonProperty("title")
    @Size(max = 150, message = "Tiêu đề không được vượt quá 150 ký tự")
    private String title;

    @JsonProperty("background")
    @NotBlank(message = "Yêu cầu Background")
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

    /** One of CharacterVoices.SUPPORTED. Empty means the default voice. */
    @JsonProperty("voiceId")
    private String voiceId;

    /** How the character refers to itself, e.g. "ta". Empty means "tôi". */
    @Size(max = 30, message = "Cách xưng của nhân vật tối đa 30 ký tự")
    @JsonProperty("selfPronoun")
    private String selfPronoun;

    /** How the character addresses the learner, e.g. "ngươi". Empty means "bạn". */
    @Size(max = 30, message = "Cách gọi người học tối đa 30 ký tự")
    @JsonProperty("addressPronoun")
    private String addressPronoun;

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
    private Boolean isPublished = false;

    @JsonProperty("contextId")
    @Schema(deprecated = true, description = "Deprecated: use mapping APIs to link character with historical contexts")
    private String contextId;

    @JsonProperty("contextIds")
    @Schema(deprecated = true, description = "Deprecated: use mapping APIs to link character with historical contexts")
    private List<String> contextIds;

    public Boolean getIsDraft() {
        return isPublished == null ? null : !isPublished;
    }
}
