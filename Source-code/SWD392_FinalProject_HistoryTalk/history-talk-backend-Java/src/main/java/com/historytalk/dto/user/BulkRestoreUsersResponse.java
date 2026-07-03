package com.historytalk.dto.user;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BulkRestoreUsersResponse {
    private int restoredCount;
    private List<String> restoredUserIds;
    private List<String> failedUserIds;
}
