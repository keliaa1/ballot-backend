package com.ballot.dto.ballot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BallotOptionResponse {

    private final String id;
    private final String optionText;
    private final int displayOrder;
}
