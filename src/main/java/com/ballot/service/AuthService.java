package com.ballot.service;

import com.ballot.dto.auth.*;
import com.ballot.entity.*;
import com.ballot.event.*;
import com.ballot.exception.*;
import com.ballot.repository.UserRepository;
import com.ballot.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authentication service handling registration, login with lockout, email verification,
 * password reset, and JWT token management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${application.mail.base-url}")
    private String baseUrl;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    // ===== REGISTRATION =====

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new DuplicateResourceException("An account with email '" + request.getEmail() + "' already exists.");
        }

        String verificationToken = UUID.randomUUID().toString();
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .status(UserStatus.PENDING_VERIFICATION)
                .role(Role.USER)
                .emailVerificationToken(verificationToken)
                .emailVerificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();
        user = userRepository.save(user);

        String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + verificationToken;
        notificationService.sendEmailVerification(user, verificationUrl);
        eventPublisher.publishEvent(new UserRegisteredEvent(this, user.getId(), ipAddress, userAgent));

        log.info("User registered: {}", user.getEmail());
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // ===== LOGIN =====

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        // Account lock check
        if (!user.isAccountNonLocked()) {
            throw new AccountLockedException(
                    "Account is locked due to too many failed login attempts. Try again after " +
                    LOCK_DURATION_MINUTES + " minutes.");
        }

        // Email verification check
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email address before logging in.");
        }

        // Account status check
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new UnauthorizedException("Your account has been disabled.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()));
        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw new UnauthorizedException("Invalid email or password.");
        }

        // Successful login — reset failed attempts
        userRepository.resetFailedLoginAttempts(user.getId());
        // Reload user to get fresh state
        user = userRepository.findById(user.getId()).orElseThrow();

        eventPublisher.publishEvent(new UserLoginEvent(this, user.getId(), ipAddress, userAgent));

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setFailedLoginAttempts(attempts);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
            log.warn("Account locked for user: {}", user.getEmail());
        } else {
            userRepository.incrementFailedLoginAttempts(user.getId());
        }
    }

    // ===== LOGOUT =====

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        tokenService.blacklistToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revokeRefreshToken(refreshToken);
        }
    }

    // ===== REFRESH TOKEN =====

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!tokenService.isRefreshTokenValid(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }

        String email = jwtService.extractUsername(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Rotate tokens
        tokenService.revokeRefreshToken(request.getRefreshToken());
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = tokenService.createRefreshToken(user);
        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    // ===== EMAIL VERIFICATION =====

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired email verification token."));

        if (user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Email verification token has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
        log.info("Email verified for user: {}", user.getEmail());
    }

    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        userRepository.findByEmail(request.getEmail().toLowerCase()).ifPresent(user -> {
            if (user.isEmailVerified()) {
                throw new BadRequestException("Email is already verified.");
            }
            String verificationToken = UUID.randomUUID().toString();
            user.setEmailVerificationToken(verificationToken);
            user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);

            String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + verificationToken;
            notificationService.sendEmailVerification(user, verificationUrl);
            log.info("Verification email resent to: {}", user.getEmail());
        });
    }

    // ===== PASSWORD RESET =====

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail().toLowerCase()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

            String resetUrl = baseUrl + "/api/v1/auth/reset-password?token=" + resetToken;
            notificationService.sendPasswordResetEmail(user, resetUrl);
            log.info("Password reset email sent to: {}", user.getEmail());
        });
        // Always return success to prevent email enumeration
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token."));

        if (user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset successful for user: {}", user.getEmail());
    }

    // ===== HELPERS =====

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}
