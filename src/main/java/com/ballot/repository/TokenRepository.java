package com.ballot.repository;

import com.ballot.entity.Token;
import com.ballot.entity.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {

    Optional<Token> findByToken(String token);

    boolean existsByTokenAndRevokedFalse(String token);

    List<Token> findAllByUserIdAndTypeAndRevokedFalse(String userId, TokenType type);

    @Modifying
    @Query("UPDATE Token t SET t.revoked = true WHERE t.user.id = :userId AND t.type = :type AND t.revoked = false")
    int revokeAllUserTokens(String userId, TokenType type);

    @Modifying
    @Query("DELETE FROM Token t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(LocalDateTime now);
}
