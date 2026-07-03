package com.historytalk.dto.user;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRestoreUsersRequest {
    @NotEmpty(message = "User IDs list cannot be empty")
    private List<String> userIds;
}
