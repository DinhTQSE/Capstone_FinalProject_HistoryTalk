# User Management Module Implementation Summary

Last updated: 2026-05-28

## Scope

Implemented user profile and system-admin user management for the Java backend using Option A: profile fields are stored directly on the existing `User` entity.

No separate `user_profile` table was introduced.

## Data Model Changes

Updated:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/user/User.java
```

Added fields:

```text
fullName
dob
gender
phoneNumber
address
avatarUrl
```

Added enum:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/enums/Gender.java
```

Enum values:

```text
MALE
FEMALE
OTHER
```

## Migration

Added:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V15__add_user_profile_fields.sql
```

Migration adds profile columns to `historical_schema."user"`:

```text
full_name
dob
gender
phone_number
address
avatar_url
```

## API Endpoints

Added authenticated user profile endpoints:

```http
GET   /Historical-tell/api/v1/users/me
PATCH /Historical-tell/api/v1/users/me
PATCH /Historical-tell/api/v1/users/me/password
```

Allowed roles:

```text
CUSTOMER
CONTENT_ADMIN
SYSTEM_ADMIN
```

Added system-admin endpoints:

```http
GET   /Historical-tell/api/v1/admin/users
GET   /Historical-tell/api/v1/admin/users/{userId}
PATCH /Historical-tell/api/v1/admin/users/{userId}
PATCH /Historical-tell/api/v1/admin/users/{userId}/role
PATCH /Historical-tell/api/v1/admin/users/{userId}/deactivate
```

Allowed role:

```text
SYSTEM_ADMIN
```

## Files Added

Controllers:

```text
src/main/java/com/historytalk/controller/user/UserController.java
src/main/java/com/historytalk/controller/user/AdminUserController.java
```

Service:

```text
src/main/java/com/historytalk/service/user/UserService.java
src/main/java/com/historytalk/service/user/UserServiceImpl.java
```

DTOs:

```text
src/main/java/com/historytalk/dto/user/UserProfileResponse.java
src/main/java/com/historytalk/dto/user/UpdateMyProfileRequest.java
src/main/java/com/historytalk/dto/user/AdminUpdateUserRequest.java
src/main/java/com/historytalk/dto/user/UpdateUserRoleRequest.java
src/main/java/com/historytalk/dto/user/ChangePasswordRequest.java
```

Mapper:

```text
src/main/java/com/historytalk/mapper/user/UserMapper.java
src/main/java/com/historytalk/mapper/user/UserMapperImpl.java
```

Entity and migration:

```text
src/main/java/com/historytalk/entity/enums/Gender.java
src/main/resources/db/migration/V15__add_user_profile_fields.sql
```

Tests:

```text
src/test/java/com/historytalk/service/user/UserServiceImplTest.java
```

## Files Modified

```text
src/main/java/com/historytalk/entity/user/User.java
```

Removed placeholder mapper files:

```text
src/main/java/com/historytalk/mapper/user/UserInformationMapper.java
src/main/java/com/historytalk/mapper/user/UserInformationMapperImpl.java
```

Existing placeholder DTOs remain untouched:

```text
src/main/java/com/historytalk/dto/user/UserInformationRequest.java
src/main/java/com/historytalk/dto/user/UserInformationResponse.java
```

They are not used by the new user module.

## Behavior

`GET /users/me` returns the authenticated user's account/profile state.

`PATCH /users/me` updates profile-safe fields:

```text
userName
fullName
dob
gender
phoneNumber
address
avatarUrl
```

Self-update does not allow changes to:

```text
email
password
role
tier
token
deletedAt
```

Password changes are handled by `PATCH /users/me/password` and require:

```text
currentPassword
newPassword
confirmPassword
```

System admin can:

- list users
- view a user by id
- update profile fields
- update role through a dedicated endpoint
- deactivate a user through existing `AuthService.softDeleteUser`

Restore is not implemented in this pass because current user deactivation cascades soft-delete to user-owned content.

## Validation

Executed from:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Commands:

```powershell
mvn -q -Dtest=UserServiceImplTest test
mvn -q -DskipTests compile
mvn -q test
git diff --check
```

Results:

```text
mvn -q -Dtest=UserServiceImplTest test -> exit code 0
mvn -q -DskipTests compile             -> exit code 0
mvn -q test                            -> exit code 0
git diff --check                       -> exit code 0
```

Mockito/ByteBuddy printed JVM dynamic-agent warnings during tests. These warnings are not test failures.
