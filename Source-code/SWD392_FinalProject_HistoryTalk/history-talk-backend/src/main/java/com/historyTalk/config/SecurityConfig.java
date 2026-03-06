package com.historyTalk.config;

import com.historyTalk.security.JwtAuthenticationFilter;
import com.historyTalk.security.JwtTokenProvider;
import com.historyTalk.service.authentication.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.Collections;

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

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI and API Docs - public access
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/api/v1/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/swagger-ui/**").permitAll()

                        // Auth endpoints - register-staff requires admin auth, others are public
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register-staff").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Historical Context endpoints - GET public, mutating requires auth
                        .requestMatchers(HttpMethod.GET, "/v1/historical-contexts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/historical-contexts/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/historical-contexts/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/historical-contexts/**").authenticated()

                        // Historical Context Document endpoints
                        .requestMatchers(HttpMethod.GET, "/v1/historical-documents/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/historical-documents/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/historical-documents/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/historical-documents/**").authenticated()

                        // Character endpoints - GET public, mutating requires auth
                        .requestMatchers(HttpMethod.GET, "/v1/characters/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/characters/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/characters/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/characters/**").authenticated()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
