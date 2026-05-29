# User Management Module Plan

Last updated: 2026-05-28

## Goal

Add user profile and system-admin user management to the Java backend without introducing a separate `user_profile` table.

The chosen model is Option A: extend the existing `User` entity directly with profile fields. This keeps the implementation small and consistent with the current ERD direction.

## Current Context

Current backend path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Current `User` entity already owns account and subscription state:

```text
uid
tier
userName
email
password
role
token
lastActiveDate
createdAt
updatedAt
deletedAt
```

There was no dedicated `/api/v1/users` controller before this module. Account registration, login, logout, refresh-token, and deactivation lived under `AuthController`.

## Data Model

Add profile fields directly to `User`:

```text
fullName
dob
gender
phoneNumber
address
avatarUrl
```

Use a new enum:

```text
Gender: MALE, FEMALE, OTHER
```

Do not reintroduce `isActive`. User lifecycle stays based on `deletedAt`.

## Migration

Add:

```text
src/main/resources/db/migration/V16__add_user_profile_fields.sql
```

Migration behavior:

```sql
ALTER TABLE historical_schema."user"
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS dob DATE,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
```

## API Design

Authenticated user profile endpoints:

```http
GET   /api/v1/users/me
PATCH /api/v1/users/me
PATCH /api/v1/users/me/password
```

Allowed roles:

```text
CUSTOMER
CONTENT_ADMIN
SYSTEM_ADMIN
```

System admin user management endpoints:

```http
GET   /api/v1/admin/users
GET   /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}/role
PATCH /api/v1/admin/users/{userId}/deactivate
```

Allowed role:

```text
SYSTEM_ADMIN
```

## Request And Response DTOs

Use camelCase JSON field names. Database columns remain snake_case.

`UpdateMyProfileRequest`:

```json
{
  "userName": "string",
  "fullName": "string",
  "dob": "2000-01-01",
  "gender": "MALE",
  "phoneNumber": "0901234567",
  "address": "string",
  "avatarUrl": "https://example.com/avatar.png"
}
```

Fields intentionally excluded from self-update:

```text
email
password
role
tier
token
deletedAt
```

`ChangePasswordRequest`:

```json
{
  "currentPassword": "string",
  "newPassword": "string",
  "confirmPassword": "string"
}
```

`UpdateUserRoleRequest`:

```json
{
  "role": "CONTENT_ADMIN"
}
```

`UserProfileResponse` includes account and profile state:

```text
uid
userName
email
role
fullName
dob
gender
phoneNumber
address
avatarUrl
tierId
tierTitle
token
lastActiveDate
createdAt
updatedAt
deletedAt
```

## Business Rules

- A user cannot update their own profile after deactivation.
- Self profile update must not change `email`, `role`, `tier`, `token`, `password`, or `deletedAt`.
- Username changes must remain unique case-insensitively.
- Password change requires the current password and matching new confirmation.
- Admin profile update can edit profile fields and username, but role changes go through a dedicated role endpoint.
- Admin deactivation delegates to existing `AuthService.softDeleteUser`, preserving current cascade behavior.
- Restore user is intentionally out of scope because deactivation currently cascades soft-delete to user-owned content.

## Implementation Files

Entity and migration:

```text
entity/user/User.java
entity/enums/Gender.java
src/main/resources/db/migration/V16__add_user_profile_fields.sql
```

Controllers:

```text
controller/user/UserController.java
controller/user/AdminUserController.java
```

Service:

```text
service/user/UserService.java
service/user/UserServiceImpl.java
```

DTOs:

```text
dto/user/UserProfileResponse.java
dto/user/UpdateMyProfileRequest.java
dto/user/AdminUpdateUserRequest.java
dto/user/UpdateUserRoleRequest.java
dto/user/ChangePasswordRequest.java
```

Mapper:

```text
mapper/user/UserMapper.java
mapper/user/UserMapperImpl.java
```

Tests:

```text
src/test/java/com/historytalk/service/user/UserServiceImplTest.java
```

## Validation Plan

Run:

```powershell
mvn -q -Dtest=UserServiceImplTest test
mvn -q -DskipTests compile
mvn -q test
```

Expected result: all commands exit with code 0.
