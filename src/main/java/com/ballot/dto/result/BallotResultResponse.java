package com.ballot.dto.result;

import com.ballot.entity.BallotStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BallotResultResponse {

    private final String ballotId;
    private final String ballotTitle;
    private final BallotStatus status;
    private final long totalVotes;
    private final List<ResultResponse> results;
    private final LocalDateTime closedAt;
}
