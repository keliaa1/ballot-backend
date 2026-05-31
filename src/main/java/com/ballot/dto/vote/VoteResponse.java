package com.ballot.dto.vote;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoteResponse {

    private final String id;
    private final String ballotId;
    private final List<String> selectedOptionIds;
    /** Null for anonymous ballots in public responses. */
    private final String userId;
    private final LocalDateTime createdAt;
}
