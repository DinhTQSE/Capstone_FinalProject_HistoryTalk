package com.historytalk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolTokenPurchaseHistoryResponse {

    private String orderId;
    private Long orderCode;
    private String schoolId;
    private String schoolName;
    private Long tokens;
    private Integer amount;
    private String status;
    private String fulfillmentStatus;
    private String paidAt;
    private String createdAt;
}
