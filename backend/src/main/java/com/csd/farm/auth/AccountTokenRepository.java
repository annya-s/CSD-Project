package com.csd.farm.auth;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountTokenRepository {

    
    public enum Purpose {
        EMAIL_VERIFY, PASSWORD_RESET
    }
    public record UsableToken(UUID id, UUID farmerId) {
    }

    private final JdbcClient jdbc;
    
    public AccountTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }
        
    // store new email verify / password reset proof
    public void save(UUID id, UUID farmerId,
            Purpose purpose, String tokenHash,
            OffsetDateTime expiresAt) {

        jdbc.sql("""
                INSERT INTO farm.account_token
                        (id, farmer_id, purpose, token_hash, expires_at)
                VALUES (:id, :farmerId, :purpose, :tokenHash, :expiresAt)
                """)
                .param("id", id)
                .param("farmerId", farmerId)
                .param("purpose", purpose.name())
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .update();
    }

    // find token that match token_hash & purpose & unused & unexpired.
    //      else ret Optional.empty()
    public Optional<UsableToken> findUsable(
            String tokenHash, Purpose purpose) {

        return jdbc.sql("""
                SELECT id, farmer_id FROM farm.account_token
                WHERE token_hash = :tokenHash   AND purpose = :purpose
                  AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """)
                .param("tokenHash", tokenHash)
                .param("purpose", purpose.name())
                .query((row, number) -> new UsableToken(
                        row.getObject("id", UUID.class),
                        row.getObject("farmer_id", UUID.class)))
                .optional();
    }

    // consume token 
    public void markUsed(UUID tokenId) {
        jdbc.sql("""
                UPDATE farm.account_token
                SET used_at = CURRENT_TIMESTAMP
                WHERE id = :tokenId     AND used_at IS NULL
                """)
                .param("tokenId", tokenId)
                .update();
    }

    // invalidate prev token issued before new token is created
    public void invalidateUnused(UUID farmerId, Purpose purpose) {
        jdbc.sql("""
                UPDATE farm.account_token
                SET used_at = CURRENT_TIMESTAMP
                WHERE farmer_id = :farmerId 
                  AND purpose = :purpose    AND used_at IS NULL
                """)
                .param("farmerId", farmerId)
                .param("purpose", purpose.name())
                .update();
    }
}