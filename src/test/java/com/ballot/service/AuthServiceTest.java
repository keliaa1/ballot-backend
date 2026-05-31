package com.ballot.service;

import com.ballot.dto.auth.LoginRequest;
import com.ballot.dto.auth.RegisterRequest;
import com.ballot.entity.*;
import com.ballot.exception.*;
import com.ballot.repository.UserRepository;
import com.ballot.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokenService;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Inject the baseUrl via reflection
        try {
            var field = AuthService.class.getDeclaredField("baseUrl");
            field.setAccessible(true);
            field.set(authService, "http://localhost:8080");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testUser = User.builder()
                .id("user-123")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("encoded-password")
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .role(Role.USER)
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void register_shouldCreateUserAndSendVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        // Use reflection to set fields (Lombok @Getter @NoArgsConstructor)
        setField(request, "firstName", "John");
        setField(request, "lastName", "Doe");
        setField(request, "email", "john@example.com");
        setField(request, "password", "password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(tokenService.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);

        var response = authService.register(request, "127.0.0.1", "test-agent");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(notificationService).sendEmailVerification(any(), anyString());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "john@example.com");
        setField(request, "firstName", "John");
        setField(request, "lastName", "Doe");
        setField(request, "password", "password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "agent"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void login_shouldReturnTokensOnSuccess() {
        LoginRequest request = new LoginRequest();
        setField(request, "email", "john@example.com");
        setField(request, "password", "password123");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(tokenService.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        var response = authService.login(request, "127.0.0.1", "agent");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).resetFailedLoginAttempts("user-123");
    }

    @Test
    void login_shouldThrowForUnverifiedEmail() {
        testUser.setEmailVerified(false);
        testUser.setStatus(UserStatus.PENDING_VERIFICATION);

        LoginRequest request = new LoginRequest();
        setField(request, "email", "john@example.com");
        setField(request, "password", "password123");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "agent"))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_shouldThrowForLockedAccount() {
        testUser.setLockedUntil(LocalDateTime.now().plusHours(1));

        LoginRequest request = new LoginRequest();
        setField(request, "email", "john@example.com");
        setField(request, "password", "password123");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "agent"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_shouldIncrementFailedAttemptsOnBadCredentials() {
        LoginRequest request = new LoginRequest();
        setField(request, "email", "john@example.com");
        setField(request, "password", "wrong-password");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "agent"))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository).incrementFailedLoginAttempts("user-123");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
