package com.ballot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BallotNotEditableException extends RuntimeException {
    public BallotNotEditableException(String message) {
        super(message);
    }
}
