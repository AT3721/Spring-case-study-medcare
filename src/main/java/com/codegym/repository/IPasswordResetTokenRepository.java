package com.codegym.repository;

import com.codegym.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IPasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
}
