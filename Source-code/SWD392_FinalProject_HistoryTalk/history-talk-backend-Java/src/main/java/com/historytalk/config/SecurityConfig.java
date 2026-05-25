package com.historytalk.config;

import com.historytalk.security.JwtAuthenticationFilter;
import com.historytalk.security.JwtTokenProvider;
import com.historytalk.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.historytalk.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.historytalk.service.authentication.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ⚠️ SHARED FILE - Team Coordination Required
 *
 * This file manages API security for ALL modules.
 * When adding new modules, coordinate before modifying this file.
 *
 * ENDPOINT CONVENTION:
 * - /v1/historical-contexts/**  → Historical Context Module
 * - /v1/historical-documents/** → Historical Context Document Module
 * - /api/v1/auth/**             → Auth Module (public)
 * - /swagger-ui/**              → Public (Swagger)
 * - /v3/api-docs/**             → Public (OpenAPI spec)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Value("${monitoring.allowed-ips:127.0.0.1,0:0:0:0:0:0:0:1}")
    private String monitoringAllowedIps;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .access((authentication, context) ->
                                new AuthorizationDecision(isMonitoringIpAllowed(context.getRequest())))
                        .anyRequest().denyAll()
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        .requestMatchers("/api/v1/api-docs/**", "/api/v1/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/Historical-tell/oauth2/**", "/Historical-tell/login/oauth2/**").permitAll()

                        // Payment: webhook must be public (PayOS servers have no JWT)
                        // Tiers listing is public (pricing page — no auth needed)
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/payos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/payos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/tiers").permitAll()

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
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/Historical-tell/oauth2/authorization"))
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/Historical-tell/login/oauth2/code/*"))
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureHandler(oauth2AuthenticationFailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private boolean isMonitoringIpAllowed(HttpServletRequest request) {
        Set<String> allowedIps = List.of(monitoringAllowedIps.split(","))
                .stream()
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .collect(Collectors.toSet());

        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String firstForwardedIp = forwardedFor == null || forwardedFor.isBlank()
                ? null
                : forwardedFor.split(",")[0].trim();

        return isIpInAllowedList(remoteAddr, allowedIps)
                || (firstForwardedIp != null && isIpInAllowedList(firstForwardedIp, allowedIps));
    }

    private boolean isIpInAllowedList(String candidateIp, Set<String> allowedIps) {
        if (candidateIp == null || candidateIp.isBlank()) {
            return false;
        }
        return allowedIps.stream()
                .anyMatch(allowed -> new IpAddressMatcher(allowed).matches(candidateIp));
    }

//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//                .csrf(csrf -> csrf.disable())
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .authenticationProvider(authenticationProvider())
//                .authorizeHttpRequests(auth -> auth
//                        // Swagger UI and API Docs - public access
//                        .requestMatchers("/v3/api-docs/**").permitAll()
//                        .requestMatchers("/swagger-ui/**").permitAll()
//                        .requestMatchers("/swagger-ui.html").permitAll()
//                        .requestMatchers("/webjars/**").permitAll()
//                        .requestMatchers("/api/v1/api-docs/**").permitAll()
//                        .requestMatchers("/api/v1/swagger-ui/**").permitAll()
//
//                        // Auth endpoints - register-staff requires admin auth, others are public
//                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register-staff").permitAll()
//                        .requestMatchers("/api/v1/auth/**").permitAll()
//
//                        // Historical Context endpoints - GET public, mutating requires auth
//                        .requestMatchers(HttpMethod.GET, "/v1/historical-contexts/**").permitAll()
//                        .requestMatchers(HttpMethod.POST, "/v1/historical-contexts/**").authenticated()
//                        .requestMatchers(HttpMethod.PUT, "/v1/historical-contexts/**").authenticated()
//                        .requestMatchers(HttpMethod.DELETE, "/v1/historical-contexts/**").authenticated()
//
//                        // Historical Context Document endpoints
//                        .requestMatchers(HttpMethod.GET, "/v1/historical-documents/**").permitAll()
//                        .requestMatchers(HttpMethod.POST, "/v1/historical-documents/**").authenticated()
//                        .requestMatchers(HttpMethod.PUT, "/v1/historical-documents/**").authenticated()
//                        .requestMatchers(HttpMethod.DELETE, "/v1/historical-documents/**").authenticated()
//
//                        // Character endpoints - GET public, mutating requires auth
//                        .requestMatchers(HttpMethod.GET, "/v1/characters/**").permitAll()
//                        .requestMatchers(HttpMethod.POST, "/v1/characters/**").authenticated()
//                        .requestMatchers(HttpMethod.PUT, "/v1/characters/**").authenticated()
//                        .requestMatchers(HttpMethod.DELETE, "/v1/characters/**").authenticated()
//
//                        // Chat endpoints - all require authentication (user owns their sessions)
//                        .requestMatchers("/v1/chat/**").authenticated()
//
//                        // All other endpoints require authentication
//                        .anyRequest().authenticated()
//                )
//                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//        configuration.setAllowedOrigins(Collections.singletonList("*"));
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
//        configuration.setAllowedHeaders(Arrays.asList("*"));
//        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
//        configuration.setAllowCredentials(false);
//        configuration.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }

//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
//        configuration.setAllowedHeaders(Arrays.asList("*"));
//        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
//        configuration.setAllowCredentials(true);
//        configuration.setMaxAge(3600L);
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
