package com.ballot.repository;

import com.ballot.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, String> {

    List<Vote> findAllByBallotIdAndUserId(String ballotId, String userId);

    boolean existsByBallotIdAndUserId(String ballotId, String userId);

    void deleteByBallotIdAndUserId(String ballotId, String userId);

    long countByBallotId(String ballotId);

    long countByBallotIdAndOptionId(String ballotId, String optionId);

    List<Vote> findAllByBallotId(String ballotId);

    @Query("SELECT v.option.id, COUNT(v) FROM Vote v WHERE v.ballot.id = :ballotId GROUP BY v.option.id")
    List<Object[]> countVotesByOptionForBallot(String ballotId);
}
