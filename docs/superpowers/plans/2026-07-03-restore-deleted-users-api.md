# Restore Deleted Users API Implementation Plan

Detailed plan to implement new REST APIs for restoring deactivated (soft-deleted) users in the system. These endpoints will allow System Administrators to restore a single user, multiple users in batch, or all deactivated users.

---

## User Review Required

> [!IMPORTANT]
> - All restore operations will be restricted exclusively to the `SYSTEM_ADMIN` role, consistent with existing user management/deactivation policies in [AdminUserController.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/user/AdminUserController.java).
> - Restoring a user clears their `deletedAt` field (sets it to `null`).
> - The batch restore API will identify which users were successfully restored and which ones failed (e.g. because they did not exist or were already active).

---

## Open Questions

> [!NOTE]
> None. The requirements for restoring users (individually, in batch, and all) are clear.

---

## Proposed Changes

We will modify the user management layer (DTO, Repository, Service, and Controller) to support the three restore APIs.

### 1. DTO Component

We need to define request and response payloads for batch restoration.

#### [NEW] [BulkRestoreUsersRequest.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/user/BulkRestoreUsersRequest.java)
- Define a request DTO that accepts a list of user ID strings.
```java
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
```

#### [NEW] [BulkRestoreUsersResponse.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/user/BulkRestoreUsersResponse.java)
- Define a response DTO containing statistics of the batch operation.
```java
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
```

---

### 2. Repository Component

We will add `@Modifying` queries to `UserRepository` to support efficient bulk updates.

#### [MODIFY] [UserRepository.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/UserRepository.java)
- Add database-level update queries:
```java
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.deletedAt = null WHERE u.deletedAt IS NOT NULL")
    int restoreAllUsers();
```

---

### 3. Service Component

Define and implement the restore logic.

#### [MODIFY] [UserService.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/user/UserService.java)
- Add new method declarations:
```java
    UserProfileResponse restoreUser(String userId);

    BulkRestoreUsersResponse restoreUsersBatch(java.util.List<String> userIds);

    int restoreAllUsers();
```

#### [MODIFY] [UserServiceImpl.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/user/UserServiceImpl.java)
- Implement `restoreUser`:
  1. Load the user (even if deactivated).
  2. If `deletedAt == null`, throw `InvalidRequestException` indicating user is already active.
  3. Set `deletedAt = null` and save user.
- Implement `restoreUsersBatch`:
  1. Parse list of UUIDs.
  2. Query existing users in batch.
  3. Filter users who are currently deactivated (`deletedAt != null`).
  4. Set `deletedAt = null` for all matching users and batch save.
  5. Calculate successfully restored IDs and failed IDs (IDs in request that did not exist or were already active).
  6. Return `BulkRestoreUsersResponse`.
- Implement `restoreAllUsers`:
  1. Trigger `userRepository.restoreAllUsers()`.
  2. Return the number of restored accounts.

---

### 4. Controller Component

Expose the new APIs under the System Admin user management endpoints.

#### [MODIFY] [AdminUserController.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/user/AdminUserController.java)
- Add the following request handlers:
  - **Restore Single User**:
    `PATCH /{userId}/restore`
  - **Restore Multiple Users (Batch)**:
    `PATCH /restore/batch`
  - **Restore All Users**:
    `PATCH /restore/all`

---

## Verification Plan

### Automated Tests
- Create JUnit integration tests in the test suite to verify:
  1. Deactivating a user sets `deletedAt` and prevents normal login.
  2. Restoring a user clears `deletedAt` and re-enables normal login.
  3. Batch restoring active and inactive users correctly updates the inactive ones and returns accurate statistics.
  4. Restoring all users clears `deletedAt` for every deactivated account.
  5. Attempting to call these endpoints with a non-admin role returns `403 Forbidden`.

### Manual Verification
- Deploy and verify endpoints via Swagger UI (`http://localhost:8080/Historical-tell/api/v1/swagger-ui/index.html`):
  1. Deactivate a user using `PATCH /api/v1/admin/users/{userId}/deactivate`.
  2. Verify they are listed as deactivated (e.g. `deletedAt` is populated).
  3. Call `PATCH /api/v1/admin/users/{userId}/restore` and verify `deletedAt` becomes `null`.
  4. Deactivate multiple users, call `PATCH /api/v1/admin/users/restore/batch` with their IDs, and verify they are all active again.
  5. Call `PATCH /api/v1/admin/users/restore/all` to restore any remaining deactivated users.
