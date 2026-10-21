package br.com.store_land.repository;

import br.com.store_land.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash AND t.expiresAt > CURRENT_TIMESTAMP AND t.usedAt IS NULL")
    Optional<PasswordResetToken> findByTokenHashValid(@Param("tokenHash") String tokenHash);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt WHERE t.userId = :userId AND t.usedAt IS NULL")
    void markAsUsed(@Param("userId") Long userId, @Param("usedAt") OffsetDateTime usedAt);
}
