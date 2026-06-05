package com.historytalk.dto.trash;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTrashActionRequest {

    @NotEmpty(message = "Yêu cầu ids")
    private List<String> ids;
}
