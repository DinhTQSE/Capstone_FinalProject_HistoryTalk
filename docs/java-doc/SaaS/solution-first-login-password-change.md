# Technical Solution: Luồng Bắt Buộc Đổi Mật Khẩu Lần Đầu (First-Login Password Change Enforcement)
**Mã giải pháp:** SOL-AUTH-FIRSTLOGIN-01  
**Kiến trúc:** Spring Boot 3 + Spring Security + JWT Stateless  
**Dự án:** HistoryTalk Platform  
**Trạng thái:** Design Approved (Cách 1: Role Tạm Thời `ROLE_PRE_CHANGE_PASSWORD`)  

---

## 1. TỔNG QUAN GIẢI PHÁP (ARCHITECTURE OVERVIEW)

### 1.1. Vấn đề Cần Giải Quyết
Khi người dùng (Đặc biệt là Học sinh & Giáo viên) được cấp tài khoản với mật khẩu ban đầu ngẫu nhiên:
1. Cần ép buộc người dùng **phải đổi mật khẩu ngay trong lần đầu tiên đăng nhập** thành công.
2. Nếu chưa đổi mật khẩu, toàn bộ các API nghiệp vụ (Chat AI, Làm bài Quiz, Xem Lớp...) **phải bị khóa hoàn toàn**.
3. **Xử lý sự cố Token Hết hạn**: Nếu Token hết hạn khi chưa kịp đổi mật khẩu, hoặc người dùng đăng nhập lại sau đó, hệ thống vẫn giữ cờ `is_first_login = true` trong DB và tiếp tục áp dụng cơ chế khóa này một cách tự nhiên.

### 1.2. Nguyên lý Hoạt động (Temporary Authority Strategy)
Thay vì dùng các câu lệnh `if-else` kiểm tra URL phức tạp trong Filter, giải pháp áp dụng cơ chế **Gán Role Tạm Thời (GrantedAuthority)** của Spring Security:
* Nếu người dùng đăng nhập thành công và DB ghi nhận `is_first_login == true`: `JwtAuthenticationFilter` giải mã Token và **tước bỏ Role thực tế**, chỉ gán cho Request duy nhất Authority: **`ROLE_PRE_CHANGE_PASSWORD`**.
* Trong `SecurityConfig`, chỉ các API `/api/v1/auth/change-password`, `/api/v1/auth/me`, `/logout` mới cho phép `ROLE_PRE_CHANGE_PASSWORD` truy cập. Các API nghiệp vụ khác chỉ cho phép các Role chính thức (`ROLE_SCHOOL_STUDENT`, `ROLE_CUSTOMER`...) $\rightarrow$ Hệ thống tự động trả về `403 Forbidden` nếu người dùng cố tình gọi API khác.

---

## 2. TRỌN BỘ MÃ NGUỒN HIỆN THỰC (IMPLEMENTATION CODE)

### Component 1: `JwtTokenProvider.java`
Thêm cờ `is_first_login` vào Claims khi cấp Access Token tại màn hình Đăng nhập.

```java
package com.historytalk.security;

import com.historytalk.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-in-ms}")
    private long jwtExpirationInMs;

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ROLE_" + user.getRole().name());
        claims.put("is_first_login", Boolean.TRUE.equals(user.getIsFirstLogin()));
        claims.put("school_id", user.getSchool() != null ? user.getSchool().getSchoolId() : null);

        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUserName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

---

### Component 2: `JwtAuthenticationFilter.java`
Bộ lọc intercept mỗi Request, đọc cờ `is_first_login` từ Token và gán `ROLE_PRE_CHANGE_PASSWORD` nếu chưa đổi mật khẩu.

```java
package com.historytalk.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            Claims claims = tokenProvider.getClaimsFromToken(token);
            String username = claims.getSubject();
            Boolean isFirstLogin = claims.get("is_first_login", Boolean.class);
            String actualRole = claims.get("role", String.class);

            // 1. Phân quyền Authority dựa trên cờ is_first_login
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (Boolean.TRUE.equals(isFirstLogin)) {
                // CHỈ CẤP ROLE TẠM THỜI
                authorities.add(new SimpleGrantedAuthority("ROLE_PRE_CHANGE_PASSWORD"));
            } else {
                // CẤP ROLE CHÍNH THỨC
                authorities.add(new SimpleGrantedAuthority(actualRole));
            }

            // 2. Set vào Spring SecurityContext
            UserPrincipal principal = new UserPrincipal(username, isFirstLogin, authorities);
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

---

### Component 3: `SecurityConfig.java`
Cấu hình Spring Security khóa toàn bộ các API ngoại trừ API đổi mật khẩu đối với `ROLE_PRE_CHANGE_PASSWORD`.

```java
package com.historytalk.config;

import com.historytalk.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1. Public Endpoints
                .requestMatchers("/api/v1/auth/login", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                
                // 2. Endpoints CHO PHÉP ROLE_PRE_CHANGE_PASSWORD TRUY CẬP
                .requestMatchers(
                    "/api/v1/auth/change-password", 
                    "/api/v1/auth/me", 
                    "/api/v1/auth/logout"
                ).hasAnyAuthority("ROLE_PRE_CHANGE_PASSWORD", "ROLE_SCHOOL_STUDENT", "ROLE_TEACHER", "ROLE_SCHOOL_ADMIN")

                // 3. Toàn bộ API Nghiệp vụ khác -> BẮT BUỘC Phải có Role chính thức
                .requestMatchers("/api/v1/chat/**").hasAnyRole("CUSTOMER", "SCHOOL_STUDENT", "TEACHER")
                .requestMatchers("/api/v1/quizzes/**").hasAnyRole("CUSTOMER", "SCHOOL_STUDENT", "TEACHER")
                .requestMatchers("/api/v1/schools/**").hasAnyRole("SYSTEM_ADMIN", "ADMIN")
                
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

### Component 4: `AuthServiceImpl.java` (Logic Đổi Mật Khẩu & Cấp Token Mới)

```java
package com.historytalk.service.impl;

import com.historytalk.dto.request.ChangePasswordRequest;
import com.historytalk.dto.response.AuthResponse;
import com.historytalk.entity.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse changePasswordFirstLogin(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new InvalidRequestException("Tài khoản không tồn tại!"));

        // 1. Validation mật khẩu mới
        if (request.newPassword().equals(request.oldPassword())) {
            throw new InvalidRequestException("Mật khẩu mới không được trùng với mật khẩu ban đầu!");
        }

        // 2. Mã hóa & Cập nhật Mật khẩu mới
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        
        // 3. Đánh dấu ĐÃ ĐỔI MẬT KHẨU LẦN ĐẦU
        user.setIsFirstLogin(false);
        userRepository.save(user);

        // 4. Sinh TOKEN MỚI TINH (Lúc này is_first_login = false, gán Role chính thức)
        String newAccessToken = tokenProvider.generateAccessToken(user);

        return new AuthResponse(newAccessToken, "Đổi mật khẩu thành công! Tài khoản đã được kích hoạt hoàn toàn.");
    }
}
```

---

## 3. KỊCH BẢN XỬ LÝ EDGE CASES (SECURITY EDGE CASES)

### Kịch bản 1: Access Token hết hạn trước khi kịp Đổi mật khẩu
* **Hiện tượng:** Học sinh đăng nhập thành công, nhận Token ban đầu nhưng treo máy 30 phút $\rightarrow$ Token hết hạn.
* **Xử lý:** Do trong DB cờ `is_first_login` vẫn là `true`, khi học sinh đăng nhập lại bằng `Username` + `Mật khẩu ban đầu`, Backend xác thực thành công và cấp một Access Token MỚI TINH (vẫn mang cờ `is_first_login: true`). Màn hình đổi mật khẩu tiếp tục hiển thị bình thường.

### Kịch bản 2: Cố tình gọi API Chat AI bằng Postman/Curl
* **Hiện tượng:** Kẻ gian lấy Token ban đầu và gửi Request trực tiếp tới `POST /api/v1/chat/sessions`.
* **Xử lý:** Spring Security kiểm tra Token chỉ chứa Authority `ROLE_PRE_CHANGE_PASSWORD`. Đối chiếu với `SecurityConfig` thấy Endpoint `/api/v1/chat/**` yêu cầu `ROLE_SCHOOL_STUDENT` $\rightarrow$ Lập tức quăng lỗi **`403 Forbidden`**.

### Kịch bản 3: Tắt trình duyệt giữa chừng
* **Hiện tượng:** Người dùng đổi mật khẩu thất bại hoặc đóng app.
* **Xử lý:** Mật khẩu ban đầu trong DB chưa bị thay đổi. Lần truy cập sau người dùng dùng lại mật khẩu ban đầu để vào luồng kích hoạt.
