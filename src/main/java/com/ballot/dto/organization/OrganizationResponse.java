package com.ballot.dto.organization;

import com.ballot.entity.JoinType;
import com.ballot.entity.OrganizationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationResponse {

    private final String id;
    private final String name;
    private final String description;
    private final String logoUrl;
    private final String inviteCode;
    private final JoinType joinType;
    private final String createdById;
    private final String createdByName;
    private final OrganizationStatus status;
    private final long memberCount;
    private final com.ballot.entity.MembershipRole myRole;
    private final LocalDateTime createdAt;
}
