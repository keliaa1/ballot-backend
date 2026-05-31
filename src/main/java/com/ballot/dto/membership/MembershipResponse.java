package com.ballot.dto.membership;

import com.ballot.entity.MembershipRole;
import com.ballot.entity.MembershipStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MembershipResponse {

    private final String membershipId;
    private final String userId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String profileImage;
    private final MembershipRole role;
    private final MembershipStatus status;
    private final LocalDateTime joinedAt;
}
