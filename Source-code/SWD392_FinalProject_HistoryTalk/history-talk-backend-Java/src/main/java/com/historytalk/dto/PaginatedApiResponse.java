package com.historytalk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedApiResponse<T> {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private List<T> data;

    @JsonProperty("pagination")
    private PaginationMeta pagination;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("errorCode")
    private String errorCode;

    public static <T> PaginatedApiResponse<T> of(PaginatedResponse<T> pr, String message) {
        return PaginatedApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(pr != null && pr.getContent() != null ? pr.getContent() : List.of())
                .pagination(pr != null ? PaginationMeta.builder()
                        .page(pr.getCurrentPage())
                        .size(pr.getPageSize())
                        .totalElements(pr.getTotalElements())
                        .totalPages(pr.getTotalPages())
                        .hasNext(pr.getHasNext())
                        .hasPrevious(pr.getHasPrevious())
                        .build() : null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
