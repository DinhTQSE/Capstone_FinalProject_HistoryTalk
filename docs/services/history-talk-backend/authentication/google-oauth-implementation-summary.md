# Google OAuth Backend Implementation Summary

## Status

Backend Google OAuth integration is implemented for the Spring Security OAuth2 Login flow.

Latest re-check:

```text
GOOGLE_CLIENT_ID=SET
GOOGLE_CLIENT_SECRET=SET
FRONTEND_OAUTH_SUCCESS_URL=SET
FRONTEND_OAUTH_FAILURE_URL=SET
```

No secret values are documented here.

## Backend Flow

The frontend starts Google login by redirecting the browser to the backend OAuth authorization URL:

```text
http://localhost:8080/Historical-tell/oauth2/authorization/google
```

Spring Security uses the `google` registration id from:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
```

After Google authenticates the user, Google redirects back to the backend callback:

```text
http://localhost:8080/Historical-tell/login/oauth2/code/google
```

Spring Security handles the authorization code exchange, retrieves the Google user principal, then calls the backend success handler.

## Frontend Integration Contract

Frontend can either call the discovery endpoint:

```http
POST /Historical-tell/api/v1/auth/google/login-url
```

Expected response data:

```json
{
  "url": "/Historical-tell/oauth2/authorization/google"
}
```

Or frontend can directly redirect the browser to:

```text
http://localhost:8080/Historical-tell/oauth2/authorization/google
```

Do not call this URL with AJAX/fetch. It is a browser redirect flow.

## Success Redirect

After successful Google login, backend redirects browser to:

```properties
app.oauth2.success-redirect-url=${FRONTEND_OAUTH_SUCCESS_URL:http://localhost:5173/auth/google/success}
```

The success URL receives these query parameters:

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

Example shape:

```text
http://localhost:5173/auth/google/success?accessToken=<jwt>&refreshToken=<jwt>&tokenType=Bearer&expiresIn=3600000&uid=<uuid>&userName=<name>&email=<email>&role=CUSTOMER
```

Frontend should store/use `accessToken` the same way it handles normal email/password login.

## Failure Redirect

After failed Google login, backend redirects browser to:

```properties
app.oauth2.failure-redirect-url=${FRONTEND_OAUTH_FAILURE_URL:http://localhost:5173/auth/google/failure}
```

Failure query parameter:

```text
error=google_oauth_failed
```

## User Mapping Rules

Implemented in:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java
```

Rules:

```text
1. Extract email from Google OAuth2 principal.
2. Lowercase email.
3. Find local user by email.
4. If user exists and is active, use existing uid/role.
5. If user does not exist, create a new CUSTOMER user.
6. If user is soft-deleted, reject login.
7. Generate HistoryTalk JWT access/refresh tokens.
```

For newly created Google users:

```text
email    = Google email
userName = generated from Google display name or email prefix
role     = CUSTOMER
password = random BCrypt placeholder
```

The placeholder password is not a Google password. It exists only because the current database schema has `password NOT NULL`.

## JWT Result

Google OAuth does not replace the application JWT.

Google proves identity. HistoryTalk still issues its own JWT with local application data:

```json
{
  "sub": "user@gmail.com",
  "uid": "local-user-uuid",
  "role": "CUSTOMER",
  "iat": "...",
  "exp": "..."
}
```

## Security Configuration

Implemented in:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java
```

Relevant behavior:

```text
/oauth2/**       public
/login/oauth2/** public
oauth2Login      enabled
JWT filter       still active for API Bearer tokens
session policy   IF_REQUIRED
```

`IF_REQUIRED` is needed because OAuth authorization-code login uses temporary session state while redirecting to and from Google.

## Config Files

Application properties:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties
```

Expected OAuth entries:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email

app.oauth2.success-redirect-url=${FRONTEND_OAUTH_SUCCESS_URL:http://localhost:5173/auth/google/success}
app.oauth2.failure-redirect-url=${FRONTEND_OAUTH_FAILURE_URL:http://localhost:5173/auth/google/failure}
```

The explicit Spring redirect URI is currently commented out:

```properties
# spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
```

This is acceptable because Spring Security's default callback pattern is the same. If Google returns `redirect_uri_mismatch`, uncomment this line and verify the Google Console URI exactly matches:

```text
http://localhost:8080/Historical-tell/login/oauth2/code/google
```

Local secret file:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/secretKey.properties
```

Required keys:

```properties
GOOGLE_CLIENT_ID=<local value>
GOOGLE_CLIENT_SECRET=<local value>
FRONTEND_OAUTH_SUCCESS_URL=http://localhost:5173/auth/google/success
FRONTEND_OAUTH_FAILURE_URL=http://localhost:5173/auth/google/failure
```

## Files Added

```text
src/main/java/com/historytalk/config/OAuth2Properties.java
src/main/java/com/historytalk/service/authentication/GoogleOAuthService.java
src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java
src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandler.java
src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationFailureHandler.java
src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java
src/test/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
```

## Files Modified

```text
pom.xml
src/main/resources/application.properties
src/main/java/com/historytalk/config/SecurityConfig.java
src/main/java/com/historytalk/controller/authentication/AuthController.java
```

## Verification

Run from backend folder:

```powershell
mvn -q test
```

Latest verification result:

```text
exit code 0
```

## FE Checklist

Frontend should:

```text
1. Redirect browser to /Historical-tell/oauth2/authorization/google.
2. Implement route /auth/google/success.
3. Read accessToken, refreshToken, tokenType, expiresIn, uid, userName, email, role from query params.
4. Store token using the same logic as normal login.
5. Use Authorization: Bearer <accessToken> for protected APIs.
6. Implement route /auth/google/failure and display login failure state.
```

## Production Notes

Tokens in query parameters are simple for integration but can leak through browser history/logs. Before production, prefer a short-lived one-time code flow:

```text
backend success handler -> frontend success URL with one-time code
frontend -> backend exchange endpoint
backend -> JWT response
```

Also ensure Google client secret is never committed and rotate any secret that was previously exposed in tracked files or logs.

