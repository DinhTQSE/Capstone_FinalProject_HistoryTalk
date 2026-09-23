# 👥 HistoryTalk Java Backend - User Management Specification

This specification documents user profile management, avatar updates, monthly token allowance resets, and password reset token handling.

---

## 1. User Entity Schema & Properties

```sql
CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id                     UUID REFERENCES historical_schema.tier(tier_id) ON DELETE SET NULL,
    user_name                   VARCHAR(100) NOT NULL UNIQUE,
    email                       VARCHAR(100) NOT NULL UNIQUE,
    password                    VARCHAR(255) NOT NULL,
    role                        VARCHAR(50) NOT NULL, -- CUSTOMER | CONTENT_ADMIN | SYSTEM_ADMIN
    token                       INT NOT NULL DEFAULT 0,
    full_name                   VARCHAR(100),
    avatar_url                  VARCHAR(500),
    phone_number                VARCHAR(20),
    last_active_date            TIMESTAMP,
    last_token_reset_at         TIMESTAMP,
    password_reset_token        VARCHAR(255),
    password_reset_token_expiry TIMESTAMP,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP,
    deleted_at                  TIMESTAMP
);
```

---

## 2. Monthly Token Reset & Password Reset

1. **Monthly Token Reset**:
   - `last_token_reset_at` tracks when the user's monthly token allowance was last refreshed.
   - Upon initial login or subscription upgrade, tokens reset to the tier allowance (`free`: 20, `plus`: 100, `pro`: 999).
2. **Password Reset Token Flow**:
   - `password_reset_token` stores SHA-256 hashed reset token.
   - `password_reset_token_expiry` enforces 15-minute expiration (`app.password-reset.token-expiration-minutes`).

---

## 3. User Management API Endpoints Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | Authenticated | Get current logged-in user profile & active tier info. |
| `PUT` | `/api/v1/users/me` | Authenticated | Update user full name, phone number, and avatar URL. |
| `GET` | `/api/v1/users` | `SYSTEM_ADMIN` | List all system users with role & tier filters. |
| `DELETE` | `/api/v1/users/{uid}` | `SYSTEM_ADMIN` | Deactivate/soft-delete a user account. |
