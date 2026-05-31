package com.ballot.repository;

import com.ballot.entity.BallotOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BallotOptionRepository extends JpaRepository<BallotOption, String> {

    List<BallotOption> findAllByBallotIdOrderByDisplayOrder(String ballotId);

    long countByBallotId(String ballotId);

    void deleteAllByBallotId(String ballotId);
}
