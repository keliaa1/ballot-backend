package com.ballot.repository;

import com.ballot.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultRepository extends JpaRepository<Result, String> {

    List<Result> findAllByBallotIdOrderByVoteCountDesc(String ballotId);

    Optional<Result> findByBallotIdAndOptionId(String ballotId, String optionId);

    boolean existsByBallotId(String ballotId);

    void deleteAllByBallotId(String ballotId);
}
