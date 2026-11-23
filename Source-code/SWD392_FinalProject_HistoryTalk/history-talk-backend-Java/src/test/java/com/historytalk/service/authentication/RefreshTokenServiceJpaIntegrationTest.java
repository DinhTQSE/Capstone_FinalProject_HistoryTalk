package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.entity.authentication.RefreshToken;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.authentication.RefreshTokenRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies real Hibernate flush / transaction behavior for refresh token rotation
 * and reuse-detection family revocation against an H2 PostgreSQL-mode schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RefreshTokenServiceImpl.class)
@TestPropertySource(properties = {
        "DB_SCHEMA=historical_schema",
        "JWT_REFRESH_EXPIRATION_MS=604800000",
        "spring.datasource.url=jdbc:h2:mem:refresh_token_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS historical_schema",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.schema=historical_schema",
        "spring.jpa.properties.hibernate.default_schema=historical_schema",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenServiceJpaIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @MockBean
    private JwtService jwtService;

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    @Test
    void rotateRefreshToken_succeedsForB2bSchoolUserWithRealHibernateFlush() throws Exception {
        School school = schoolRepository.saveAndFlush(School.builder()
                .name("THPT Le Hong Phong")
                .schoolCode("LHP_RTR_" + UUID.randomUUID().toString().substring(0, 6))
                .contactEmail("admin@lhp-rtr.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .build());

        User student = userRepository.saveAndFlush(User.builder()
                .userName("lhp_hs_rtr1")
                .email("rtr.student@lhp-rtr.edu.vn")
                .password("$2a$10$dummyEncodedPasswordHashValue00000000000000000000")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .build());

        when(jwtService.generateAccessToken(eq(student.getEmail()), anyMap())).thenReturn("rotated-access-jwt");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        String initialRawToken = refreshTokenService.createRefreshToken(student, "127.0.0.1", "Browser");
        RefreshToken initialEntity = refreshTokenRepository.findByTokenHash(sha256Hex(initialRawToken)).orElseThrow();
        UUID familyId = initialEntity.getFamilyId();

        RefreshTokenResponse response = refreshTokenService.rotateRefreshToken(initialRawToken, "127.0.0.2", "Browser-2");

        assertThat(response.getAccessToken()).isEqualTo("rotated-access-jwt");
        assertThat(response.getRefreshToken()).isNotBlank().isNotEqualTo(initialRawToken);

        List<RefreshToken> familyTokens = refreshTokenRepository.findByFamilyId(familyId);
        assertThat(familyTokens).hasSize(2);

        RefreshToken reloadedInitial = refreshTokenRepository.findByTokenHash(sha256Hex(initialRawToken)).orElseThrow();
        RefreshToken reloadedRotated = refreshTokenRepository.findByTokenHash(sha256Hex(response.getRefreshToken())).orElseThrow();
        assertThat(reloadedInitial.isRevoked()).isTrue();
        assertThat(reloadedRotated.isRevoked()).isFalse();
        assertThat(reloadedRotated.getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void rotateRefreshToken_replayingRevokedToken_throwsUnauthorizedAndCommitsFamilyRevocation() throws Exception {
        User customer = userRepository.saveAndFlush(User.builder()
                .userName("rtr_customer")
                .email("rtr.customer@historytalk.vn")
                .password("$2a$10$dummyEncodedPasswordHashValue00000000000000000000")
                .role(UserRole.CUSTOMER)
                .build());

        when(jwtService.generateAccessToken(eq(customer.getEmail()), anyMap())).thenReturn("rotated-access-jwt");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        String token1 = refreshTokenService.createRefreshToken(customer, "10.0.0.1", "Device-A");
        UUID familyId = refreshTokenRepository.findByTokenHash(sha256Hex(token1)).orElseThrow().getFamilyId();

        RefreshTokenResponse rotated = refreshTokenService.rotateRefreshToken(token1, "10.0.0.1", "Device-A");
        String token2 = rotated.getRefreshToken();

        // Replaying already-rotated token1 must trigger reuse detection and persist family revocation
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(token1, "9.9.9.9", "Attacker"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Refresh token đã bị thu hồi trước đó");

        List<RefreshToken> familyAfterReplay = refreshTokenRepository.findByFamilyId(familyId);
        assertThat(familyAfterReplay).hasSize(2);
        assertThat(familyAfterReplay).allMatch(RefreshToken::isRevoked);

        // Even legitimate active token2 must now be rejected
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(token2, "10.0.0.1", "Device-A"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Refresh token đã bị thu hồi trước đó");
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
