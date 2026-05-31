package com.ballot.dto.ballot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class CreateBallotRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Organization ID is required")
    private String organizationId;

    private boolean anonymous = false;
    private boolean multipleChoice = false;
    private boolean startImmediately = false;

    private LocalDateTime startDate;

    @NotNull(message = "End date is required")
    @Future(message = "End date must be in the future")
    private LocalDateTime endDate;

    @NotNull(message = "Options are required")
    @Size(min = 2, max = 10, message = "Ballot must have between 2 and 10 options")
    @Valid
    private List<BallotOptionRequest> options;
}
