# Google OAuth Frontend API Contract

## Purpose

This contract defines how the frontend integrates with the backend-driven Google OAuth login flow.

Backend service:

```text
HistoryTalk Java Backend
```

Local backend base URL:

```text
http://localhost:8080
```

Servlet path:

```text
/Historical-tell
```

Effective local API base:

```text
http://localhost:8080/Historical-tell
```

## Flow Summary

This is a browser redirect flow, not an AJAX login flow.

```text
FE redirects browser to backend Google OAuth URL
-> backend redirects browser to Google
-> Google redirects browser back to backend callback
-> backend finds/creates local user
-> backend creates HistoryTalk JWT tokens
-> backend redirects browser to FE success URL with token query params
```

## Backend Configuration Assumptions

Backend must have these runtime values configured:

```properties
GOOGLE_CLIENT_ID=<set>
GOOGLE_CLIENT_SECRET=<set>
FRONTEND_OAUTH_SUCCESS_URL=http://localhost:5173/auth/google/success
FRONTEND_OAUTH_FAILURE_URL=http://localhost:5173/auth/google/failure
```

Google Cloud Console must include this Authorized redirect URI:

```text
http://localhost:8080/Historical-tell/login/oauth2/code/google
```

For production:

```text
https://<backend-domain>/Historical-tell/login/oauth2/code/google
```

## Registration ID

The Spring Security OAuth registration id is:

```text
google
```

It comes from the property prefix:

```properties
spring.security.oauth2.client.registration.google.*
```

Therefore:

```text
Authorization URL: /Historical-tell/oauth2/authorization/google
Callback URL:      /Historical-tell/login/oauth2/code/google
```

The frontend does not need to send `registrationId` separately.

## Endpoint 1: Get Google Login URL

This endpoint lets FE discover the backend OAuth start URL.

```http
POST /Historical-tell/api/v1/auth/google/login-url
```

Auth required:

```text
No
```

Request body:

```text
None
```

Success response:

```http
200 OK
Content-Type: application/json
```

Response body:

```json
{
  "success": true,
  "message": "Google login URL generated successfully",
  "data": {
    "url": "/Historical-tell/oauth2/authorization/google"
  },
  "timestamp": "2026-05-24T20:00:00.000000",
  "errorCode": null
}
```

TypeScript contract:

```ts
type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
  errorCode: string | null;
};

type GoogleLoginUrlResponse = ApiResponse<{
  url: string;
}>;
```

Frontend behavior:

```ts
const response = await fetch(
  `${BACKEND_BASE_URL}/Historical-tell/api/v1/auth/google/login-url`,
  { method: "POST" }
);

const body = (await response.json()) as GoogleLoginUrlResponse;

if (body.success && body.data?.url) {
  window.location.href = `${BACKEND_BASE_URL}${body.data.url}`;
}
```

Frontend may skip this endpoint and redirect directly to:

```text
http://localhost:8080/Historical-tell/oauth2/authorization/google
```

## Endpoint 2: Start Google OAuth Login

This is a browser navigation URL.

```http
GET /Historical-tell/oauth2/authorization/google
```

Auth required:

```text
No
```

Frontend must call this by browser redirect:

```ts
window.location.href = "http://localhost:8080/Historical-tell/oauth2/authorization/google";
```

Do not call this with:

```text
fetch
axios
XHR
```

Expected behavior:

```text
Backend returns a redirect to Google login/consent.
```

## Endpoint 3: Google Callback

This endpoint is handled by Spring Security and Google redirects to it automatically.

```http
GET /Historical-tell/login/oauth2/code/google
```

Frontend must not call this directly.

Google Cloud Console must whitelist it exactly.

## Success Redirect To Frontend

After backend completes Google authentication, it redirects to:

```text
FRONTEND_OAUTH_SUCCESS_URL
```

Local default:

```text
http://localhost:5173/auth/google/success
```

Backend appends these query parameters:

```text
accessToken
refreshToken
tokenType
expiresIn
uid
userName
email
role
```

Example:

```text
http://localhost:5173/auth/google/success?accessToken=<access-token>&refreshToken=<refresh-token>&tokenType=Bearer&expiresIn=3600000&uid=<uuid>&userName=<username>&email=user@gmail.com&role=CUSTOMER
```

TypeScript contract:

```ts
type GoogleOAuthSuccessQuery = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: string;
  uid: string;
  userName: string;
  email: string;
  role: "CUSTOMER" | "CONTENT_ADMIN" | "SYSTEM_ADMIN";
};
```

Frontend success route behavior:

```ts
const params = new URLSearchParams(window.location.search);

const accessToken = params.get("accessToken");
const refreshToken = params.get("refreshToken");
const tokenType = params.get("tokenType");
const expiresIn = params.get("expiresIn");
const uid = params.get("uid");
const userName = params.get("userName");
const email = params.get("email");
const role = params.get("role");

if (!accessToken || !refreshToken || tokenType !== "Bearer" || !uid || !email || !role) {
  // Treat as failed login and redirect to login page.
}

// Store tokens using the same mechanism as normal login.
```

After storing tokens, frontend should remove tokens from the URL:

```ts
window.history.replaceState({}, document.title, "/auth/google/success");
```

Then redirect user to the authenticated area, for example:

```text
/dashboard
```

## Failure Redirect To Frontend

After failed Google authentication, backend redirects to:

```text
FRONTEND_OAUTH_FAILURE_URL
```

Local default:

```text
http://localhost:5173/auth/google/failure
```

Query params:

```text
error=google_oauth_failed
```

Example:

```text
http://localhost:5173/auth/google/failure?error=google_oauth_failed
```

TypeScript contract:

```ts
type GoogleOAuthFailureQuery = {
  error: "google_oauth_failed";
};
```

Frontend behavior:

```text
Show login failure state and allow user to retry Google login.
```

## Token Usage After Success

After Google OAuth success, tokens are the same HistoryTalk JWT tokens as normal email/password login.

Use the access token for protected APIs:

```http
Authorization: Bearer <accessToken>
```

Refresh token can be used with the existing refresh endpoint:

```http
POST /Historical-tell/api/v1/auth/refresh-token
Content-Type: application/json
```

Request body:

```json
{
  "refreshToken": "<refresh-token>"
}
```

Expected success response data:

```json
{
  "accessToken": "<new-access-token>",
  "refreshToken": "<same-refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

## Local User Creation Rules

If Google email already exists in the backend user table:

```text
Backend uses the existing user id and role.
```

If Google email does not exist:

```text
Backend creates a new CUSTOMER user.
```

New Google-created user fields:

```text
email    = Google email
userName = generated from Google display name or email prefix
role     = CUSTOMER
password = random BCrypt placeholder
```

The placeholder password is not the user's Google password.

If the matched user is soft-deleted:

```text
Backend rejects login and redirects to failure URL.
```

## FE Error Handling Matrix

| Scenario | Backend behavior | FE expected behavior |
|---|---|---|
| User cancels Google login | Redirects to failure URL | Show retry message |
| Google auth fails | Redirects to failure URL with `error=google_oauth_failed` | Show retry message |
| Google email missing | Redirects to failure URL | Show retry message |
| Local user is soft-deleted | Redirects to failure URL | Show account disabled/help message if supported |
| Success URL missing token params | FE detects invalid query | Clear auth state and return to login |
| Access token expired later | Protected API returns auth error | Use refresh-token flow or logout |

## FE Implementation Checklist

- [ ] Add "Continue with Google" button on login page.
- [ ] On click, redirect browser to `/Historical-tell/oauth2/authorization/google` or call login-url endpoint first.
- [ ] Add route `/auth/google/success`.
- [ ] Parse success query params.
- [ ] Validate required token/user fields.
- [ ] Store tokens using existing auth storage.
- [ ] Remove query params from browser URL after storing tokens.
- [ ] Redirect user to authenticated destination.
- [ ] Add route `/auth/google/failure`.
- [ ] Display retry/error state on failure.
- [ ] Ensure API client sends `Authorization: Bearer <accessToken>`.
- [ ] Reuse existing refresh-token flow.

## Security Notes For FE

Tokens are currently returned in query params for simple integration.

Frontend should remove query params immediately after reading them:

```ts
window.history.replaceState({}, document.title, "/auth/google/success");
```

Avoid logging full callback URLs because they contain JWTs.

For production hardening, replace query-token redirect with a one-time exchange code:

```text
backend -> FE success URL with code
FE -> backend exchange endpoint
backend -> JWT response
```

## Contract Verification

Backend verification command:

```powershell
mvn -q test
```

Latest known backend verification:

```text
exit code 0
```

Recommended FE consumer checks:

```text
1. `POST /api/v1/auth/google/login-url` returns data.url string.
2. Login button uses browser redirect, not fetch.
3. Success route accepts all required query params.
4. Failure route handles `error=google_oauth_failed`.
5. Token storage and protected API calls match normal login behavior.
```

