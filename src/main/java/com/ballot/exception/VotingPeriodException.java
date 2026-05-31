package com.ballot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class VotingPeriodException extends RuntimeException {
    public VotingPeriodException(String message) {
        super(message);
    }
}
