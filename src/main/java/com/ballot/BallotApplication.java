package com.ballot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Ballot platform backend.
 * Organisation-based online voting platform built with Spring Boot 3 + Java 21.
 */
@SpringBootApplication
@EnableScheduling
public class BallotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BallotApplication.class, args);
    }
}
