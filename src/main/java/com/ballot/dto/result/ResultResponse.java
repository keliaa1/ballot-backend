package com.ballot.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultResponse {

    private final String optionId;
    private final String optionText;
    private final long voteCount;
    private final BigDecimal percentage;
    private final LocalDateTime computedAt;
}
