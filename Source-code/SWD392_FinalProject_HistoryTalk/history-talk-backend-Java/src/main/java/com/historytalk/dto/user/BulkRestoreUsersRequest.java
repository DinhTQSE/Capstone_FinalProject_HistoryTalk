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
    @NotEmpty(message = "Danh sách ID người dùng không được để trống")
    private List<String> userIds;
}
