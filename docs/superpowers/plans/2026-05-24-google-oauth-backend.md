# Google OAuth Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-driven Google OAuth login that creates or finds a HistoryTalk user, then issues the same HistoryTalk JWT access/refresh token pair used by normal email/password login.

**Architecture:** Use Spring Security OAuth2 Login for the Google authorization-code flow. Google authenticates the user and returns control to the backend callback; the backend maps Google email to a local `User`, generates HistoryTalk JWTs with existing `JwtService`, then redirects the browser back to the frontend with login result data.

**Tech Stack:** Spring Boot 3.2.5, Spring Security OAuth2 Client, Spring MVC, JPA, JJWT, Java 21, Maven, PostgreSQL/Flyway.

---

## Current Context

Backend root:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Relevant existing files:

```text
pom.xml
src/main/resources/application.properties
src/main/resources/secretKey.properties
src/main/java/com/historytalk/config/SecurityConfig.java
src/main/java/com/historytalk/controller/authentication/AuthController.java
src/main/java/com/historytalk/service/authentication/AuthService.java
src/main/java/com/historytalk/service/authentication/AuthServiceImpl.java
src/main/java/com/historytalk/service/authentication/JwtService.java
src/main/java/com/historytalk/dto/authentication/LoginResponse.java
src/main/java/com/historytalk/entity/user/User.java
src/main/java/com/historytalk/repository/UserRepository.java
src/main/java/com/historytalk/security/JwtAuthenticationFilter.java
src/main/java/com/historytalk/security/JwtTokenProvider.java
```

Existing dependency state:

```text
spring-boot-starter-oauth2-client already exists in pom.xml
spring-boot-starter-security already exists in pom.xml
jjwt dependencies already exist in pom.xml
```

Existing servlet path:

```properties
spring.mvc.servlet.path=/Historical-tell
```

Local Google authorized redirect URI must be:

```text
http://localhost:8080/Historical-tell/login/oauth2/code/google
```

Production Google authorized redirect URI must use the deployed backend domain:

```text
https://<backend-domain>/Historical-tell/login/oauth2/code/google
```

Current risk to fix:

```text
Google client secret is hardcoded in application.properties. Rotate the current secret in Google Cloud Console before production use, then inject it through environment variables or secretKey.properties.
```

## File Structure

Create:

```text
src/main/java/com/historytalk/config/OAuth2Properties.java
src/main/java/com/historytalk/service/authentication/GoogleOAuthService.java
src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java
src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandler.java
src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationFailureHandler.java
src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java
src/test/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
```

Modify:

```text
src/main/resources/application.properties
src/main/resources/secretKey.properties
src/main/java/com/historytalk/config/SecurityConfig.java
```

Do not modify the `User` schema for the first implementation. The current `password` column is `NOT NULL`, so Google-created accounts will receive a random BCrypt placeholder password that cannot be used directly by the user.

---

### Task 1: Move Google OAuth Configuration To Safe Properties

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify local only: `src/main/resources/secretKey.properties`

- [ ] **Step 1: Replace hardcoded Google credentials in `application.properties`**

Change the Google OAuth section to this exact shape:

```properties
# =============================================
# Google OAuth2 Client
# =============================================
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}

# Frontend redirects after backend OAuth processing
app.oauth2.success-redirect-url=${FRONTEND_OAUTH_SUCCESS_URL:http://localhost:5173/auth/google/success}
app.oauth2.failure-redirect-url=${FRONTEND_OAUTH_FAILURE_URL:http://localhost:5173/auth/google/failure}
```

- [ ] **Step 2: Add local secret keys to `secretKey.properties`**

Add these keys locally. Do not commit real secret values.

```properties
GOOGLE_CLIENT_ID=<your-google-client-id>
GOOGLE_CLIENT_SECRET=<your-google-client-secret>
FRONTEND_OAUTH_SUCCESS_URL=http://localhost:5173/auth/google/success
FRONTEND_OAUTH_FAILURE_URL=http://localhost:5173/auth/google/failure
```

- [ ] **Step 3: Verify Google Cloud Console redirect URI**

In Google Cloud Console, set Authorized redirect URI:

```text
http://localhost:8080/Historical-tell/login/oauth2/code/google
```

If production is deployed, also add:

```text
https://<backend-domain>/Historical-tell/login/oauth2/code/google
```

- [ ] **Step 4: Compile-check property loading**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

Do not commit `secretKey.properties`; it is ignored.

```powershell
git add src/main/resources/application.properties
git commit -m "chore(auth): externalize google oauth configuration"
```

---

### Task 2: Add OAuth2 Redirect Properties Bean

**Files:**
- Create: `src/main/java/com/historytalk/config/OAuth2Properties.java`

- [ ] **Step 1: Create `OAuth2Properties.java`**

```java
package com.historytalk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class OAuth2Properties {

    @Value("${app.oauth2.success-redirect-url}")
    private String successRedirectUrl;

    @Value("${app.oauth2.failure-redirect-url}")
    private String failureRedirectUrl;
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/historytalk/config/OAuth2Properties.java
git commit -m "feat(auth): add oauth2 redirect properties"
```

---

### Task 3: Add Google OAuth Service Contract

**Files:**
- Create: `src/main/java/com/historytalk/service/authentication/GoogleOAuthService.java`

- [ ] **Step 1: Create interface**

```java
package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface GoogleOAuthService {

    LoginResponse authenticateGoogleUser(OAuth2User oauth2User);
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/historytalk/service/authentication/GoogleOAuthService.java
git commit -m "feat(auth): define google oauth service contract"
```

---

### Task 4: Add Google OAuth Service Tests

**Files:**
- Create: `src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private GoogleOAuthServiceImpl googleOAuthService;

    @Test
    void authenticateGoogleUser_createsCustomerWhenEmailDoesNotExist() {
        OAuth2User oauth2User = oauth2User("new.user@gmail.com", "New User");
        User saved = user("new.user", "new.user@gmail.com", UserRole.CUSTOMER);

        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUserNameIgnoreCase("new.user")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-placeholder");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateAccessToken(eq("new.user@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("new.user@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        LoginResponse response = googleOAuthService.authenticateGoogleUser(oauth2User);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(userCaptor.getValue().getUserName()).isEqualTo("new.user");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-placeholder");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void authenticateGoogleUser_reusesExistingActiveUser() {
        User existing = user("existing", "existing@gmail.com", UserRole.CONTENT_ADMIN);
        OAuth2User oauth2User = oauth2User("existing@gmail.com", "Existing User");

        when(userRepository.findByEmailIgnoreCase("existing@gmail.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(eq("existing@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("existing@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        LoginResponse response = googleOAuthService.authenticateGoogleUser(oauth2User);

        assertThat(response.getUid()).isEqualTo(existing.getUid().toString());
        assertThat(response.getUserName()).isEqualTo("existing");
        assertThat(response.getRole()).isEqualTo("CONTENT_ADMIN");
    }

    @Test
    void authenticateGoogleUser_rejectsSoftDeletedUser() {
        User deleted = user("deleted", "deleted@gmail.com", UserRole.CUSTOMER);
        deleted.setDeletedAt(LocalDateTime.now());

        when(userRepository.findByEmailIgnoreCase("deleted@gmail.com")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> googleOAuthService.authenticateGoogleUser(oauth2User("deleted@gmail.com", "Deleted User")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account has been deactivated");
    }

    @Test
    void authenticateGoogleUser_rejectsMissingEmail() {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(),
                Map.of("name", "No Email"),
                "name"
        );

        assertThatThrownBy(() -> googleOAuthService.authenticateGoogleUser(oauth2User))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google account email is required");
    }

    private OAuth2User oauth2User(String email, String name) {
        return new DefaultOAuth2User(
                List.of(),
                Map.of("email", email, "name", name),
                "email"
        );
    }

    private User user(String userName, String email, UserRole role) {
        return User.builder()
                .uid(UUID.randomUUID())
                .userName(userName)
                .email(email)
                .password("encoded-password")
                .role(role)
                .build();
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
mvn -q -Dtest=GoogleOAuthServiceImplTest test
```

Expected:

```text
Compilation failure: cannot find symbol class GoogleOAuthServiceImpl
```

- [ ] **Step 3: Commit failing tests**

```powershell
git add src/test/java/com/historytalk/service/authentication/GoogleOAuthServiceImplTest.java
git commit -m "test(auth): cover google oauth user mapping"
```

---

### Task 5: Implement Google OAuth Service

**Files:**
- Create: `src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java`

- [ ] **Step 1: Create implementation**

```java
package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final String GOOGLE_PASSWORD_PREFIX = "GOOGLE_OAUTH_";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public LoginResponse authenticateGoogleUser(OAuth2User oauth2User) {
        String email = extractRequiredEmail(oauth2User);
        String displayName = extractDisplayName(oauth2User);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleUser(email, displayName));

        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Account has been deactivated");
        }

        UserPrincipal principal = new UserPrincipal(user);
        Map<String, Object> claims = buildClaims(principal);
        String accessToken = jwtService.generateAccessToken(user.getEmail(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        log.info("Google OAuth login successful for uid: {}", user.getUid());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .uid(user.getUid().toString())
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private String extractRequiredEmail(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (!StringUtils.hasText(email)) {
            throw new UnauthorizedException("Google account email is required");
        }
        return email.toLowerCase();
    }

    private String extractDisplayName(OAuth2User oauth2User) {
        String name = oauth2User.getAttribute("name");
        return StringUtils.hasText(name) ? name : null;
    }

    private User createGoogleUser(String email, String displayName) {
        String userName = generateUniqueUserName(email, displayName);
        User user = User.builder()
                .userName(userName)
                .email(email)
                .password(passwordEncoder.encode(GOOGLE_PASSWORD_PREFIX + UUID.randomUUID()))
                .role(UserRole.CUSTOMER)
                .build();
        return userRepository.save(user);
    }

    private String generateUniqueUserName(String email, String displayName) {
        String base = sanitizeUserName(StringUtils.hasText(displayName)
                ? displayName
                : email.substring(0, email.indexOf("@")));

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUserNameIgnoreCase(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String sanitizeUserName(String input) {
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9._-]", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");

        if (sanitized.length() < 3) {
            sanitized = "user" + sanitized;
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    private Map<String, Object> buildClaims(UserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getUid());
        claims.put("role", principal.getRole().name());
        return claims;
    }
}
```

- [ ] **Step 2: Run service tests**

Run:

```powershell
mvn -q -Dtest=GoogleOAuthServiceImplTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Compile project**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/historytalk/service/authentication/GoogleOAuthServiceImpl.java
git commit -m "feat(auth): map google oauth users to local accounts"
```

---

### Task 6: Add OAuth2 Success Handler Tests

**Files:**
- Create: `src/test/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java`

- [ ] **Step 1: Write failing success handler tests**

```java
package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.service.authentication.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private OAuth2Properties oauth2Properties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    void onAuthenticationSuccess_redirectsToFrontendWithLoginResult() throws Exception {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(),
                Map.of("email", "user@gmail.com"),
                "email"
        );
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .uid("user-id")
                .userName("user")
                .email("user@gmail.com")
                .role("CUSTOMER")
                .build();

        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(googleOAuthService.authenticateGoogleUser(oauth2User)).thenReturn(loginResponse);
        when(oauth2Properties.getSuccessRedirectUrl()).thenReturn("http://localhost:5173/auth/google/success");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("http://localhost:5173/auth/google/success?accessToken=access-token&refreshToken=refresh-token&tokenType=Bearer&expiresIn=3600000&uid=user-id&userName=user&email=user%40gmail.com&role=CUSTOMER");
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
mvn -q -Dtest=OAuth2AuthenticationSuccessHandlerTest test
```

Expected:

```text
Compilation failure: cannot find symbol class OAuth2AuthenticationSuccessHandler
```

- [ ] **Step 3: Commit failing tests**

```powershell
git add src/test/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
git commit -m "test(auth): cover oauth2 success redirect"
```

---

### Task 7: Implement OAuth2 Success And Failure Handlers

**Files:**
- Create: `src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandler.java`
- Create: `src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationFailureHandler.java`

- [ ] **Step 1: Create success handler**

```java
package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.service.authentication.GoogleOAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private final GoogleOAuthService googleOAuthService;
    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        LoginResponse loginResponse = googleOAuthService.authenticateGoogleUser(oauth2User);

        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.getSuccessRedirectUrl())
                .queryParam("accessToken", loginResponse.getAccessToken())
                .queryParam("refreshToken", loginResponse.getRefreshToken())
                .queryParam("tokenType", loginResponse.getTokenType())
                .queryParam("expiresIn", loginResponse.getExpiresIn())
                .queryParam("uid", loginResponse.getUid())
                .queryParam("userName", loginResponse.getUserName())
                .queryParam("email", loginResponse.getEmail())
                .queryParam("role", loginResponse.getRole())
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
```

- [ ] **Step 2: Create failure handler**

```java
package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler implements org.springframework.security.web.authentication.AuthenticationFailureHandler {

    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.getFailureRedirectUrl())
                .queryParam("error", "google_oauth_failed")
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
```

- [ ] **Step 3: Run handler tests**

Run:

```powershell
mvn -q -Dtest=OAuth2AuthenticationSuccessHandlerTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Compile project**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationSuccessHandler.java src/main/java/com/historytalk/security/oauth2/OAuth2AuthenticationFailureHandler.java
git commit -m "feat(auth): redirect google oauth login results"
```

---

### Task 8: Wire OAuth2 Login Into SecurityConfig

**Files:**
- Modify: `src/main/java/com/historytalk/config/SecurityConfig.java`

- [ ] **Step 1: Add imports**

Add these imports:

```java
import com.historytalk.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.historytalk.security.oauth2.OAuth2AuthenticationSuccessHandler;
```

- [ ] **Step 2: Add constructor-injected handler fields**

Inside `SecurityConfig`, add:

```java
private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
```

Because the class already uses `@RequiredArgsConstructor`, Spring will inject these beans.

- [ ] **Step 3: Permit OAuth2 paths**

In `authorizeHttpRequests`, keep existing public paths and add:

```java
.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
```

Place it near the auth/Swagger permit rules.

- [ ] **Step 4: Enable OAuth2 login**

Before `.addFilterBefore(...)`, add:

```java
.oauth2Login(oauth2 -> oauth2
        .successHandler(oauth2AuthenticationSuccessHandler)
        .failureHandler(oauth2AuthenticationFailureHandler)
)
```

The final part of the main filter chain should look like:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
        .requestMatchers("/api/v1/api-docs/**", "/api/v1/swagger-ui/**").permitAll()
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

        .requestMatchers(HttpMethod.GET, "/api/v1/characters/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/character-documents/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/historical-contexts/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/historical-documents/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/quizzes/**").permitAll()

        .requestMatchers("/api/v1/chat/**").authenticated()
        .requestMatchers(HttpMethod.POST, "/api/v1/quizzes/**").authenticated()
        .requestMatchers(HttpMethod.PATCH, "/api/v1/quizzes/**").authenticated()

        .anyRequest().authenticated()
)
.oauth2Login(oauth2 -> oauth2
        .successHandler(oauth2AuthenticationSuccessHandler)
        .failureHandler(oauth2AuthenticationFailureHandler)
)
.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
```

- [ ] **Step 5: Compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/historytalk/config/SecurityConfig.java
git commit -m "feat(auth): enable google oauth2 login"
```

---

### Task 9: Add Optional AuthController Discovery Endpoint

**Files:**
- Modify: `src/main/java/com/historytalk/controller/authentication/AuthController.java`

This task is optional but useful for frontend integration because it exposes the backend login URL without hardcoding it in frontend code.

- [ ] **Step 1: Add import**

```java
import java.util.Map;
```

- [ ] **Step 2: Add endpoint**

Inside `AuthController`, add:

```java
@PostMapping("/google/login-url")
@Operation(summary = "Get Google login URL", description = "Returns backend Google OAuth2 authorization URL.")
public ResponseEntity<ApiResponse<Map<String, String>>> googleLoginUrl() {
    Map<String, String> data = Map.of("url", "/Historical-tell/oauth2/authorization/google");
    return ResponseEntity.ok(ApiResponse.success(data, "Google login URL generated successfully"));
}
```

- [ ] **Step 3: Compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/historytalk/controller/authentication/AuthController.java
git commit -m "feat(auth): expose google oauth login url"
```

---

### Task 10: Run Full Verification

**Files:**
- No file changes.

- [ ] **Step 1: Run targeted tests**

Run:

```powershell
mvn -q -Dtest=GoogleOAuthServiceImplTest,OAuth2AuthenticationSuccessHandlerTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run application**

Run:

```powershell
mvn spring-boot:run
```

Expected:

```text
Started HistoryTalkApplication
```

- [ ] **Step 4: Start Google login manually**

Open:

```text
http://localhost:8080/Historical-tell/oauth2/authorization/google
```

Expected:

```text
Browser redirects to Google login/consent screen.
After successful Google login, browser redirects to FRONTEND_OAUTH_SUCCESS_URL with accessToken, refreshToken, tokenType, expiresIn, uid, userName, email, and role query parameters.
```

- [ ] **Step 5: Verify returned access token against protected API**

Call a protected API with:

```http
Authorization: Bearer <accessToken>
```

Expected:

```text
API accepts the token and treats user as authenticated with role from local database.
```

- [ ] **Step 6: Commit verification note if docs are updated**

If README or auth handoff docs are updated with Google OAuth usage:

```powershell
git add README.md docs/services/history-talk-backend/authentication-module.md
git commit -m "docs(auth): document google oauth login flow"
```

---

## Important Security Notes

Do not keep the current Google client secret. Since it appeared in `application.properties`, rotate it in Google Cloud Console.

Do not use the Google token as the application token. Google proves identity; HistoryTalk still issues its own JWT using local `uid` and `role`.

The success handler in this plan redirects tokens in query parameters for simple frontend integration. This is easy to implement but less secure than an authorization-code handoff to the frontend. Before production, prefer replacing token query params with a short-lived backend-issued one-time code that the frontend exchanges through `/api/v1/auth/oauth2/exchange`.

The existing logout blacklist is in memory and is not checked by `JwtAuthenticationFilter` for normal access-token requests. That is separate from Google OAuth but still affects all JWT logins.

The existing `SecurityConfig` permits all `/api/v1/auth/**` routes at HTTP matcher level. Privileged auth routes rely on `@PreAuthorize`. This can work, but future hardening should explicitly permit only public auth endpoints.

