package com.ballot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    // Base64-encoded 256-bit test key
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctYmFsbG90LWFwcA==";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 900_000L, 604_800_000L);
        userDetails = User.builder()
                .username("test@ballot.app")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }

    @Test
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(userDetails);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void shouldExtractUsernameFromToken() {
        String token = jwtService.generateAccessToken(userDetails);
        String username = jwtService.extractUsername(token);
        assertThat(username).isEqualTo("test@ballot.app");
    }

    @Test
    void shouldValidateValidToken() {
        String token = jwtService.generateAccessToken(userDetails);
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void shouldRejectTokenForDifferentUser() {
        String token = jwtService.generateAccessToken(userDetails);
        UserDetails otherUser = User.builder()
                .username("other@ballot.app")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void shouldDetectExpiredToken() {
        // Create a service with 1ms expiry
        JwtService shortLivedService = new JwtService(TEST_SECRET, 1L, 604_800_000L);
        String token = shortLivedService.generateAccessToken(userDetails);
        // Wait for expiry
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        assertThat(shortLivedService.isTokenExpired(token)).isTrue();
    }

    @Test
    void shouldGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(userDetails);
        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@ballot.app");
    }

    @Test
    void shouldExtractExpirationFromToken() {
        String token = jwtService.generateAccessToken(userDetails);
        Date expiration = jwtService.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
    }
}
