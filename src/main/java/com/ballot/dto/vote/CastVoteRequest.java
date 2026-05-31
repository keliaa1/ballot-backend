package com.ballot.dto.vote;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CastVoteRequest {

    @NotEmpty(message = "At least one option must be selected")
    private List<String> selectedOptionIds;
}
