package com.ballot.dto.organization;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinOrganizationRequest {

    /** Required for INVITE_ONLY organizations; optional for APPROVAL_REQUIRED/PUBLIC. */
    private String inviteCode;

    /** Required for APPROVAL_REQUIRED; used to identify which org to request joining. */
    @NotBlank(message = "Organization ID is required")
    private String organizationId;
}
