package com.historytalk.dto.trash;

import com.historytalk.entity.enums.ContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemResponse {

    private String id;
    private String type;
    private String title;
    private ContentStatus status;
    private LocalDateTime deletedAt;
}
