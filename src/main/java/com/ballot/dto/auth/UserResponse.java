package com.ballot.dto.auth;

import com.ballot.entity.Role;
import com.ballot.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Safe user profile representation — never exposes password or sensitive tokens.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String profileImage;
    private final boolean emailVerified;
    private final UserStatus status;
    private final Role role;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
