# Google OAuth Password Email And Forgot Password Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send a one-time email containing the generated application password when Google OAuth creates a new local user, and add a secure forgot-password flow using the same SMTP setup.

**Architecture:** Keep Google OAuth as the account creation owner, but move email delivery into focused notification services. Generate a raw temporary OAuth password once, hash only the encoded value into the database, pass the raw value only to the mail service, and never persist or log it. Forgot password uses a separate short-lived reset token: store only a SHA-256 token hash and expiry on the user row, send the raw token to the user's email in a reset link, and clear token fields after a successful reset.

**Tech Stack:** Spring Boot 3.2.5, Spring Security OAuth2 Client, Spring Mail SMTP, JavaMailSender, JUnit 5, Mockito, Maven.

---

## Current Context

- Backend service path: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`.
- Google OAuth user creation currently lives in `src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java`.
- New Google users are currently saved with `passwordEncoder.encode(GOOGLE_PASSWORD_PREFIX + UUID.randomUUID())`, which loses the raw password immediately.
- Existing password-change API already exists:
  - Controller: `src/main/java/com/historytalk/controller/user/UserController.java`
  - Endpoint: `PATCH /Historical-tell/api/v1/users/me/password`
  - DTO: `src/main/java/com/historytalk/dto/user/ChangePasswordRequest.java`
  - Request body:

```json
{
  "currentPassword": "temporary-password-from-email",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```

- The API requires JWT auth, so after Google OAuth success the frontend can use the returned `accessToken` as usual.
- There is no committed mail dependency or mail service currently visible in the Java backend.
- There is no forgot-password endpoint, reset-token storage, or reset-email flow currently visible in the Java backend.

## Design Decisions

- Use Spring Mail SMTP via `spring-boot-starter-mail`.
- Add dedicated notification interfaces and implementations under `com.historytalk.service.notification`.
- Send email only when `userRepository.findByEmailIgnoreCase(email)` returns empty and a new local user is created.
- If email sending fails after user creation, do not fail Google login. Log a warning without the password, and still return JWTs. This avoids locking users out of OAuth because SMTP is temporarily unavailable.
- Password format should satisfy the current change-password DTO minimum of 6 chars and be readable enough from email. Use a generated UUID-based value with a fixed prefix, for example `HT-GOOGLE-<uuid-without-dashes-first-12>`.
- Do not include the temporary password in OAuth redirect query params, logs, API responses, or database columns.
- Forgot password must not send a new password directly. It sends a short-lived reset link containing a raw token; backend stores only the token hash.
- Forgot-password request returns the same success response whether an email exists or not, to avoid account enumeration.
- Reset token expires after 15 minutes by default and can be configured with `app.password-reset.token-expiration-minutes`.
- Reset success clears `password_reset_token_hash` and `password_reset_expires_at`.
- Update docs so frontend/backend handoff is explicit.

## File Structure

- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml`
  - Add Spring Mail dependency.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties`
  - Add SMTP env-backed properties and app mail sender address.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailService.java`
  - Interface boundary for sending Google OAuth temporary password mail.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailServiceImpl.java`
  - Spring Mail implementation.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/PasswordResetEmailService.java`
  - Interface boundary for forgot-password email delivery.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/PasswordResetEmailServiceImpl.java`
  - Spring Mail reset-link implementation.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java`
  - Generate raw temporary password, save encoded password, send mail only for new users.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/authentication/ForgotPasswordRequest.java`
  - Public forgot-password request body.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/authentication/ResetPasswordRequest.java`
  - Public reset-password request body.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/AuthService.java`
  - Add forgot-password and reset-password methods.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/AuthServiceImpl.java`
  - Generate reset token, hash token, save expiry, send reset link, validate reset token, update password.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/UserRepository.java`
  - Add reset-token hash lookup.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/authentication/AuthController.java`
  - Add public forgot-password and reset-password endpoints.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/user/User.java`
  - Add reset-token hash and expiry fields.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V19__add_password_reset_fields_to_user.sql`
  - Add nullable reset-token fields and index.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java`
  - Verify mail send for new Google user, no send for existing user, and login continues if mail fails.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailServiceImplTest.java`
  - Verify JavaMailSender receives expected email content.
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/authentication/AuthServiceImplTest.java`
  - Verify forgot-password and reset-password behavior.
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/notification/PasswordResetEmailServiceImplTest.java`
  - Verify reset email is sent with a reset link.
- Modify: `docs/services/history-talk-backend/authentication/google-oauth-implementation-summary.md`
  - Document email behavior and env vars.
- Modify: `docs/services/history-talk-backend/authentication/google-oauth-frontend-api-contract.md`
  - Tell FE that Google-created users may receive a temporary password email and can call existing password-change API.
- Modify: `docs/services/history-talk-backend/authentication/authentication-module.md`
  - Document forgot-password endpoints and reset-token security rules.

---

### Task 1: Add Spring Mail Dependency And SMTP Configuration

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties`

- [ ] **Step 1: Add Spring Mail dependency**

In `pom.xml`, add this dependency near the other Spring Boot starters:

```xml
        <!-- Email / SMTP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
```

- [ ] **Step 2: Add SMTP properties**

Append this block to `application.properties`:

```properties
# =============================================
# Mail / SMTP
# =============================================
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${MAIL_SMTP_AUTH:true}
spring.mail.properties.mail.smtp.starttls.enable=${MAIL_SMTP_STARTTLS_ENABLE:true}
spring.mail.properties.mail.smtp.starttls.required=${MAIL_SMTP_STARTTLS_REQUIRED:true}
app.mail.from=${MAIL_FROM:${MAIL_USERNAME:}}
app.password-reset.frontend-url=${FRONTEND_PASSWORD_RESET_URL:http://localhost:5173/reset-password}
app.password-reset.token-expiration-minutes=${PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES:15}
```

- [ ] **Step 3: Compile to verify dependency resolution**

Run from `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`:

```bash
mvn -q -DskipTests compile
```

Expected: build reaches compilation. If dependency download fails because of network, rerun with approved network access.

- [ ] **Step 4: Commit**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties
git commit -m "chore(java): configure SMTP mail support"
```

---

### Task 2: Create Google OAuth Password Email Service

**Files:**
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailService.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailServiceImpl.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/notification/GoogleOAuthPasswordEmailServiceImplTest.java`

- [ ] **Step 1: Write failing unit test**

Create `GoogleOAuthPasswordEmailServiceImplTest.java`:

```java
package com.historytalk.service.notification;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthPasswordEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Test
    void sendTemporaryPasswordEmailBuildsExpectedMessage() throws Exception {
        GoogleOAuthPasswordEmailServiceImpl service = new GoogleOAuthPasswordEmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@historytalk.com");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendTemporaryPasswordEmail("user@gmail.com", "New User", "HT-GOOGLE-abc123");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isSameAs(mimeMessage);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q -Dtest=GoogleOAuthPasswordEmailServiceImplTest test
```

Expected: FAIL because `GoogleOAuthPasswordEmailServiceImpl` does not exist.

- [ ] **Step 3: Create service interface**

Create `GoogleOAuthPasswordEmailService.java`:

```java
package com.historytalk.service.notification;

public interface GoogleOAuthPasswordEmailService {

    void sendTemporaryPasswordEmail(String email, String userName, String temporaryPassword);
}
```

- [ ] **Step 4: Create Spring Mail implementation**

Create `GoogleOAuthPasswordEmailServiceImpl.java`:

```java
package com.historytalk.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleOAuthPasswordEmailServiceImpl implements GoogleOAuthPasswordEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Override
    public void sendTemporaryPasswordEmail(String email, String userName, String temporaryPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Your HistoryTalk temporary password");
            helper.setText(buildEmailBody(userName, temporaryPassword), false);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send Google OAuth temporary password email", ex);
        }
    }

    private String buildEmailBody(String userName, String temporaryPassword) {
        return """
                Hello %s,

                Your HistoryTalk account was created successfully using Google OAuth.

                Temporary application password:
                %s

                You can keep this password or change it after login by calling:
                PATCH /Historical-tell/api/v1/users/me/password

                This password is for HistoryTalk email/password login only. It is not your Google password.

                HistoryTalk Team
                """.formatted(userName, temporaryPassword);
    }
}
```

- [ ] **Step 5: Run service test**

Run:

```bash
mvn -q -Dtest=GoogleOAuthPasswordEmailServiceImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/notification
git commit -m "feat(java): add Google OAuth password email service"
```

---

### Task 3: Send Temporary Password Email During New Google OAuth Registration

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java`

- [ ] **Step 1: Update test class mocks**

Add import:

```java
import com.historytalk.service.notification.GoogleOAuthPasswordEmailService;
```

Add mock field:

```java
    @Mock
    private GoogleOAuthPasswordEmailService passwordEmailService;
```

- [ ] **Step 2: Update new-user test to verify raw password is encoded and emailed**

In `authenticateGoogleUser_createsCustomerWhenEmailDoesNotExist`, replace:

```java
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-placeholder");
```

with:

```java
        ArgumentCaptor<String> rawPasswordCaptor = ArgumentCaptor.forClass(String.class);
        when(passwordEncoder.encode(rawPasswordCaptor.capture())).thenReturn("encoded-placeholder");
```

After existing `assertThat(response.getRole()).isEqualTo("CUSTOMER");`, add:

```java
        String rawPassword = rawPasswordCaptor.getValue();
        assertThat(rawPassword).startsWith("HT-GOOGLE-");
        assertThat(rawPassword).hasSizeGreaterThanOrEqualTo(16);
        verify(passwordEmailService).sendTemporaryPasswordEmail(
                "new.user@gmail.com",
                "new.user",
                rawPassword
        );
```

- [ ] **Step 3: Add test that existing users do not receive password email**

Add this test:

```java
    @Test
    void authenticateGoogleUser_doesNotSendPasswordEmailForExistingUser() {
        User existing = user("existing", "existing@gmail.com", UserRole.CUSTOMER);
        OAuth2User oauth2User = oauth2User("existing@gmail.com", "Existing User");

        when(userRepository.findByEmailIgnoreCase("existing@gmail.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(eq("existing@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("existing@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        googleOAuthService.authenticateGoogleUser(oauth2User);

        verify(passwordEmailService, org.mockito.Mockito.never())
                .sendTemporaryPasswordEmail(any(String.class), any(String.class), any(String.class));
    }
```

- [ ] **Step 4: Add test that mail failure does not block OAuth login**

Add imports:

```java
import static org.mockito.Mockito.doThrow;
```

Add this test:

```java
    @Test
    void authenticateGoogleUser_continuesLoginWhenPasswordEmailFails() {
        OAuth2User oauth2User = oauth2User("new.user@gmail.com", "New User");
        User saved = user("new.user", "new.user@gmail.com", UserRole.CUSTOMER);

        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUserNameIgnoreCase("new.user")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-placeholder");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(passwordEmailService)
                .sendTemporaryPasswordEmail(eq("new.user@gmail.com"), eq("new.user"), any(String.class));
        when(jwtService.generateAccessToken(eq("new.user@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("new.user@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        LoginResponse response = googleOAuthService.authenticateGoogleUser(oauth2User);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getEmail()).isEqualTo("new.user@gmail.com");
    }
```

- [ ] **Step 5: Run tests to verify they fail**

Run:

```bash
mvn -q -Dtest=GoogleOAuthServiceImplTest test
```

Expected: FAIL because `GoogleOAuthServiceImpl` has not injected or called `GoogleOAuthPasswordEmailService`.

- [ ] **Step 6: Modify GoogleOAuthServiceImpl dependencies**

Add import:

```java
import com.historytalk.service.notification.GoogleOAuthPasswordEmailService;
```

Add field:

```java
    private final GoogleOAuthPasswordEmailService passwordEmailService;
```

- [ ] **Step 7: Replace new user creation logic**

Replace:

```java
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleUser(email, displayName));
```

with:

```java
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleUserAndSendPasswordEmail(email, displayName));
```

- [ ] **Step 8: Replace createGoogleUser method**

Replace the existing `createGoogleUser` method with:

```java
    private User createGoogleUserAndSendPasswordEmail(String email, String displayName) {
        String temporaryPassword = generateTemporaryPassword();
        User user = createGoogleUser(email, displayName, temporaryPassword);
        sendTemporaryPasswordEmail(user, temporaryPassword);
        return user;
    }

    private User createGoogleUser(String email, String displayName, String temporaryPassword) {
        String userName = generateUniqueUserName(email, displayName);
        User user = User.builder()
                .userName(userName)
                .email(email)
                .password(passwordEncoder.encode(temporaryPassword))
                .role(UserRole.CUSTOMER)
                .build();
        return userRepository.save(user);
    }

    private String generateTemporaryPassword() {
        return "HT-GOOGLE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void sendTemporaryPasswordEmail(User user, String temporaryPassword) {
        try {
            passwordEmailService.sendTemporaryPasswordEmail(user.getEmail(), user.getUserName(), temporaryPassword);
        } catch (RuntimeException ex) {
            log.warn("Could not send Google OAuth temporary password email for uid: {}", user.getUid(), ex);
        }
    }
```

- [ ] **Step 9: Run Google OAuth service tests**

Run:

```bash
mvn -q -Dtest=GoogleOAuthServiceImplTest test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java
git commit -m "feat(java): email Google OAuth temporary passwords"
```

---

### Task 4: Add Forgot Password Token Storage And API Flow

**Files:**
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V19__add_password_reset_fields_to_user.sql`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/user/User.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/authentication/ForgotPasswordRequest.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/authentication/ResetPasswordRequest.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/AuthService.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/authentication/AuthServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/UserRepository.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/authentication/AuthController.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/PasswordResetEmailService.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/notification/PasswordResetEmailServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/authentication/AuthServiceImplTest.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/notification/PasswordResetEmailServiceImplTest.java`

- [ ] **Step 1: Create password reset migration**

Create `V19__add_password_reset_fields_to_user.sql`:

```sql
ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS password_reset_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_user_password_reset_token_hash
    ON "user" (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;
```

- [ ] **Step 2: Add reset fields to User entity**

In `User.java`, add these fields after `lastTokenResetAt`:

```java
    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private LocalDateTime passwordResetExpiresAt;
```

- [ ] **Step 3: Create forgot-password request DTO**

Create `ForgotPasswordRequest.java`:

```java
package com.historytalk.dto.authentication;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
```

- [ ] **Step 4: Create reset-password request DTO**

Create `ResetPasswordRequest.java`:

```java
package com.historytalk.dto.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 100, message = "New password must be between 6 and 100 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
```

- [ ] **Step 5: Create password reset email service interface**

Create `PasswordResetEmailService.java`:

```java
package com.historytalk.service.notification;

public interface PasswordResetEmailService {

    void sendPasswordResetEmail(String email, String userName, String resetToken);
}
```

- [ ] **Step 6: Create password reset email implementation**

Create `PasswordResetEmailServiceImpl.java`:

```java
package com.historytalk.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.password-reset.frontend-url}")
    private String frontendResetUrl;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long expirationMinutes;

    @Override
    public void sendPasswordResetEmail(String email, String userName, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Reset your HistoryTalk password");
            helper.setText(buildEmailBody(userName, resetToken), false);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send password reset email", ex);
        }
    }

    private String buildEmailBody(String userName, String resetToken) {
        String resetLink = UriComponentsBuilder.fromUriString(frontendResetUrl)
                .queryParam("token", resetToken)
                .build()
                .toUriString();

        return """
                Hello %s,

                We received a request to reset your HistoryTalk password.

                Reset link:
                %s

                This link expires in %d minutes. If you did not request this reset, ignore this email.

                HistoryTalk Team
                """.formatted(userName, resetLink, expirationMinutes);
    }
}
```

- [ ] **Step 7: Extend AuthService interface**

In `AuthService.java`, add:

```java
    void forgotPassword(String email);

    void resetPassword(String token, String newPassword, String confirmPassword);
```

- [ ] **Step 8: Add reset-token repository lookup**

In `UserRepository.java`, add this method after `findByUserNameIgnoreCase`:

```java
    Optional<User> findByPasswordResetTokenHash(String tokenHash);
```

- [ ] **Step 9: Extend AuthServiceImpl dependencies**

In `AuthServiceImpl.java`, add imports:

```java
import com.historytalk.service.notification.PasswordResetEmailService;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Base64;
```

Add fields:

```java
    private final PasswordResetEmailService passwordResetEmailService;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long passwordResetTokenExpirationMinutes;

    private static final SecureRandom PASSWORD_RESET_RANDOM = new SecureRandom();
```

- [ ] **Step 10: Implement forgotPassword**

Add this method to `AuthServiceImpl.java`:

```java
    @Override
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase().trim();
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.getDeletedAt() == null)
                .ifPresent(user -> {
                    String rawToken = generatePasswordResetToken();
                    user.setPasswordResetTokenHash(hashPasswordResetToken(rawToken));
                    user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes));
                    userRepository.save(user);
                    passwordResetEmailService.sendPasswordResetEmail(user.getEmail(), user.getUserName(), rawToken);
                });
    }
```

- [ ] **Step 11: Implement resetPassword**

Add this method to `AuthServiceImpl.java`:

```java
    @Override
    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidRequestException("Reset token is required");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new InvalidRequestException("New password and confirmation password do not match");
        }

        User user = userRepository.findByPasswordResetTokenHash(hashPasswordResetToken(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired password reset token"));

        if (user.getDeletedAt() != null ||
                user.getPasswordResetExpiresAt() == null ||
                user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Invalid or expired password reset token");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }
```

- [ ] **Step 12: Add token helper methods**

Add these helper methods to `AuthServiceImpl.java`:

```java
    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        PASSWORD_RESET_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashPasswordResetToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
```

- [ ] **Step 13: Add public auth endpoints**

In `AuthController.java`, add imports:

```java
import com.historytalk.dto.authentication.ForgotPasswordRequest;
import com.historytalk.dto.authentication.ResetPasswordRequest;
```

Add endpoints after `/login`:

```java
    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a password reset email when the account exists.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        log.info("POST /api/v1/auth/forgot-password - email: {}", request.getEmail());
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "If the email exists, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets password using a valid password reset token.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("POST /api/v1/auth/reset-password");
        authService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }
```

- [ ] **Step 14: Ensure security config permits new public endpoints**

Inspect `src/main/java/com/historytalk/config/SecurityConfig.java`. If `/api/v1/auth/**` is already public, no change is needed. If specific auth endpoints are listed, add:

```java
"/api/v1/auth/forgot-password",
"/api/v1/auth/reset-password",
```

- [ ] **Step 15: Add AuthServiceImpl tests**

Create or extend `AuthServiceImplTest.java` with tests covering:

```java
@Test
void forgotPasswordStoresHashedTokenAndSendsEmailForActiveUser() {
    // arrange userRepository.findByEmailIgnoreCase returns active user
    // act authService.forgotPassword("user@example.com")
    // assert passwordResetTokenHash is 64 hex chars, expiry is not null
    // verify passwordResetEmailService.sendPasswordResetEmail called with raw token
    // assert raw token is not equal to stored hash
}

@Test
void forgotPasswordDoesNothingWhenEmailDoesNotExist() {
    // arrange Optional.empty()
    // act
    // verify no email send and no exception
}

@Test
void resetPasswordUpdatesPasswordAndClearsResetFields() {
    // arrange matching token hash and non-expired expiry
    // act resetPassword(rawToken, "newSecret1", "newSecret1")
    // assert password encoded, token hash null, expiry null
}

@Test
void resetPasswordRejectsExpiredToken() {
    // arrange token hash with expiry in the past
    // assert UnauthorizedException
}
```

Use exact Mockito setup from the existing `AuthServiceImpl` constructor fields. Mock `UserRepository.findByPasswordResetTokenHash` directly in reset-password tests.

- [ ] **Step 16: Add PasswordResetEmailServiceImpl test**

Create `PasswordResetEmailServiceImplTest.java` similar to `GoogleOAuthPasswordEmailServiceImplTest`, but set `frontendResetUrl` and `expirationMinutes` via `ReflectionTestUtils`:

```java
ReflectionTestUtils.setField(service, "fromAddress", "noreply@historytalk.com");
ReflectionTestUtils.setField(service, "frontendResetUrl", "http://localhost:5173/reset-password");
ReflectionTestUtils.setField(service, "expirationMinutes", 15L);
```

Verify `mailSender.send(mimeMessage)` is called.

- [ ] **Step 17: Run focused tests**

Run:

```bash
mvn -q -Dtest=AuthServiceImplTest,PasswordResetEmailServiceImplTest test
```

Expected: PASS.

- [ ] **Step 18: Commit**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V19__add_password_reset_fields_to_user.sql Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk
git commit -m "feat(java): add forgot password email reset flow"
```

---

### Task 5: Document SMTP, Google OAuth Password, And Forgot Password Flows

**Files:**
- Modify: `docs/services/history-talk-backend/authentication/google-oauth-implementation-summary.md`
- Modify: `docs/services/history-talk-backend/authentication/google-oauth-frontend-api-contract.md`
- Modify: `docs/services/history-talk-backend/authentication/authentication-module.md`

- [ ] **Step 1: Update backend implementation summary**

Add a section after the current "User Mapping Rules":

````markdown
## Temporary Password Email For New Google Users

When Google OAuth creates a new local user, backend now generates a temporary HistoryTalk application password, stores only its BCrypt hash, and sends the raw temporary password once by SMTP email.

The temporary password is not the user's Google password. It only allows email/password login to HistoryTalk if the user wants that login method.

Existing users who sign in with Google do not receive a password email.

If SMTP delivery fails, Google OAuth login still succeeds and backend logs the delivery failure without logging the password.

Users can keep the temporary password or change it after OAuth login with:

```http
PATCH /Historical-tell/api/v1/users/me/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "currentPassword": "temporary-password-from-email",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```
````

Add SMTP env vars under config:

````markdown
Required SMTP environment variables:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<smtp-user>
MAIL_PASSWORD=<smtp-app-password>
MAIL_FROM=<sender-email>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_SMTP_STARTTLS_REQUIRED=true
```
````

- [ ] **Step 2: Update frontend API contract**

Add a section after "Local User Creation Rules":

````markdown
## Temporary Password Email

For brand-new users created through Google OAuth, backend sends an email to the Google account email containing a temporary HistoryTalk application password.

Frontend does not receive this password in the OAuth success redirect and must not expect it in query params.

After OAuth success, frontend can show a small account-security prompt such as:

"We sent a temporary HistoryTalk password to your Google email. You can keep it or change it in account settings."

If the user chooses to change it, call the existing authenticated endpoint:

```http
PATCH /Historical-tell/api/v1/users/me/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "currentPassword": "temporary-password-from-email",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```
````

- [ ] **Step 3: Update authentication module docs**

Add this section to `authentication-module.md` near the public auth API section:

````markdown
## Forgot Password Flow

Public endpoints:

```http
POST /Historical-tell/api/v1/auth/forgot-password
POST /Historical-tell/api/v1/auth/reset-password
```

Forgot-password request:

```json
{
  "email": "user@example.com"
}
```

Response is always success-shaped to avoid account enumeration:

```json
{
  "message": "If the email exists, a password reset link has been sent"
}
```

Reset-password request:

```json
{
  "token": "token-from-email-link",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```

Security rules:
- Backend stores only SHA-256 hash of the reset token.
- Raw token is sent only by email.
- Token expires after `app.password-reset.token-expiration-minutes`.
- Successful reset clears token hash and expiry.
- Deactivated users cannot reset password.
````

- [ ] **Step 4: Commit docs**

```bash
git add docs/services/history-talk-backend/authentication/google-oauth-implementation-summary.md docs/services/history-talk-backend/authentication/google-oauth-frontend-api-contract.md docs/services/history-talk-backend/authentication/authentication-module.md
git commit -m "docs: describe auth email password flows"
```

---

### Task 6: Final Verification

**Files:**
- Verify changed Java backend scope.

- [ ] **Step 1: Run focused tests**

Run from `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`:

```bash
mvn -q -Dtest=GoogleOAuthServiceImplTest,GoogleOAuthPasswordEmailServiceImplTest,AuthServiceImplTest,PasswordResetEmailServiceImplTest test
```

Expected: PASS.

- [ ] **Step 2: Run compile check**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Optional local SMTP smoke test**

Set these environment variables locally:

```powershell
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-google-app-password"
$env:MAIL_FROM="your-email@gmail.com"
$env:MAIL_SMTP_AUTH="true"
$env:MAIL_SMTP_STARTTLS_ENABLE="true"
$env:MAIL_SMTP_STARTTLS_REQUIRED="true"
```

Start backend:

```bash
mvn spring-boot:run
```

Then run a Google OAuth registration with a Google account that does not yet exist in `users`.

Expected:
- OAuth login succeeds.
- New user exists with role `CUSTOMER`.
- Email arrives at the Google account email.
- Email contains the temporary password.
- User can call `PATCH /Historical-tell/api/v1/users/me/password` using the OAuth access token and the emailed temporary password as `currentPassword`.
- Forgot-password request returns a generic success message.
- Reset email arrives with a reset link.
- Reset-password endpoint accepts the token and changes the password.
- The same reset token cannot be reused after success.

- [ ] **Step 4: Verify no secret leakage**

Search:

```bash
rg -n "temporaryPassword|HT-GOOGLE|passwordEmail|sendTemporaryPassword|resetToken|passwordReset" src/main/java src/test/java
```

Expected:
- Raw temporary password is only used in generation, encoding, and mail-send call.
- Raw reset token is only used in generation, email-send call, and reset request handling.
- No log statement prints the raw password.
- No log statement prints the raw reset token.
- No DTO/API response includes the raw password or reset token.

- [ ] **Step 5: Final commit if any verification fixes were needed**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java docs/services/history-talk-backend/authentication
git commit -m "fix(java): polish Google OAuth password email verification"
```

---

## Risks And Mitigations

- SMTP failure after user creation: login still succeeds; warning is logged without password. User can continue with Google OAuth even if email is delayed.
- Gmail SMTP requires an app password, not the normal Google account password. Document this in deployment notes if Gmail is used.
- Emailing passwords is inherently sensitive. This plan keeps the password out of redirects/logs/database plaintext, but a better long-term flow would be a password setup link with a short-lived token.
- Current change-password API requires `currentPassword`, so emailing the temporary password fits the existing API without adding new endpoints.
- Forgot password should use reset tokens, not emailed replacement passwords, because the user does not know their current password and the current change-password endpoint requires it.

## Self-Review

- Spec coverage: New Google users receive temporary password email; existing Google users do not; existing change-password API remains the password update path; forgot password uses email reset link with hashed expiring token.
- Placeholder scan: No unresolved placeholders remain.
- Type consistency: Service method names, package names, and DTO fields match current backend conventions.
