package com.ballot.dto.membership;

import com.ballot.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role is required")
    private MembershipRole role;
}
