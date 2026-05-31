package com.ballot.dto.ballot;

import com.ballot.entity.BallotStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BallotResponse {

    private final String id;
    private final String organizationId;
    private final String organizationName;
    private final String title;
    private final String description;
    private final boolean anonymous;
    private final boolean multipleChoice;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final BallotStatus status;
    private final String createdById;
    private final String createdByName;
    private final List<BallotOptionResponse> options;
    private final long totalVotes;
    private final boolean hasVoted;   // populated for authenticated user context
    private final LocalDateTime createdAt;
}
