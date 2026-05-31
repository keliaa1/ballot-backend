package com.ballot.dto.ballot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BallotOptionRequest {

    @NotBlank(message = "Option text is required")
    @Size(max = 500, message = "Option text cannot exceed 500 characters")
    private String optionText;
}
