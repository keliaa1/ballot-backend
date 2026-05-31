package com.ballot.service;

import com.ballot.entity.Token;
import com.ballot.entity.TokenType;
import com.ballot.entity.User;
import com.ballot.repository.TokenRepository;
import com.ballot.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Manages refresh token storage, rotation, and revocation.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final JwtService jwtService;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Transactional
    public String createRefreshToken(User user) {
        // Revoke all existing refresh tokens for this user
        tokenRepository.revokeAllUserTokens(user.getId(), TokenType.REFRESH);

        String refreshToken = jwtService.generateRefreshToken(user);
        Token token = Token.builder()
                .token(refreshToken)
                .type(TokenType.REFRESH)
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .build();
        tokenRepository.save(token);
        return refreshToken;
    }

    @Transactional
    public void blacklistToken(String accessToken) {
        // Store in blacklist with same expiry as access token
        long expirationMs = jwtService.getAccessTokenExpiration();
        Token token = Token.builder()
                .token(accessToken)
                .type(TokenType.BLACKLISTED)
                .user(null)
                .revoked(false) // 'revoked' field repurposed: false = still blacklisted
                .expiresAt(LocalDateTime.now().plusSeconds(expirationMs / 1000))
                .build();
        tokenRepository.save(token);
    }

    public boolean isRefreshTokenValid(String refreshToken) {
        return tokenRepository.findByToken(refreshToken)
                .map(token -> token.getType() == TokenType.REFRESH
                        && !token.isRevoked()
                        && token.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        tokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            tokenRepository.save(token);
        });
    }

    @Transactional
    public int cleanupExpiredTokens() {
        return tokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }
}
