package com.ballot.dto.auth;

import com.ballot.entity.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Returned on successful login or token refresh.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;   // seconds
    private final String userId;
    private final String email;
    private final String fullName;
    private final Role role;
    private final boolean emailVerified;
}
