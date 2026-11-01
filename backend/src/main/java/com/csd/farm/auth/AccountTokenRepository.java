package com.csd.farm.auth;

// [ERD-ALIGNMENT CHANGE] Verification/reset tokens now use public.ACCOUNT_TOKENS.

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountTokenRepository {

    
    public enum Purpose {
        EMAIL_VERIFY, PASSWORD_RESET, LOGIN_2FA
    }
    public record UsableToken(UUID id, Long farmerId) {
    }

    private final JdbcClient jdbc;
    public AccountTokenRepository(JdbcClient jdbc) { this.jdbc=jdbc; }

    public void save(UUID id,Long farmerId,Purpose purpose,String tokenHash,OffsetDateTime expiresAt) {
        jdbc.sql("""
            INSERT INTO public."ACCOUNT_TOKENS" ("Token_ID","User_ID","Purpose","Token_Hash","Expires_At")
            VALUES (:id,:userId,:purpose,:tokenHash,:expiresAt)
            """).param("id",id).param("userId",farmerId).param("purpose",purpose.name())
            .param("tokenHash",tokenHash).param("expiresAt",expiresAt).update();
    }

    // find token that match token_hash & purpose & unused & unexpired.
    //      else ret Optional.empty()
    public Optional<UsableToken> findUsableFor(
            Long farmerId, String tokenHash, Purpose purpose) {

        return jdbc.sql("""
                SELECT "Token_ID" AS id, "User_ID" AS farmer_id
                FROM public."ACCOUNT_TOKENS"
                WHERE "User_ID" = :farmerId
                  AND "Token_Hash" = :tokenHash
                  AND "Purpose" = :purpose
                  AND "Used_At" IS NULL
                  AND "Expires_At" > CURRENT_TIMESTAMP
                """)
                .param("farmerId", farmerId)
                .param("tokenHash", tokenHash)
                .param("purpose", purpose.name())
                .query((row, number) -> new UsableToken(
                        row.getObject("id", UUID.class),
                        row.getLong("farmer_id")))
                .optional();
    }
    public void markUsed(UUID tokenId) {
        jdbc.sql("""
                UPDATE public."ACCOUNT_TOKENS"
                SET "Used_At" = CURRENT_TIMESTAMP
                WHERE "Token_ID" = :tokenId AND "Used_At" IS NULL
                """)
                .param("tokenId", tokenId)
                .update();
    }
    public void invalidateUnused(Long farmerId,Purpose purpose) {
        jdbc.sql("""
                UPDATE public."ACCOUNT_TOKENS"
                SET "Used_At" = CURRENT_TIMESTAMP
                WHERE "User_ID" = :farmerId
                  AND "Purpose" = :purpose AND "Used_At" IS NULL
                """)
                .param("farmerId", farmerId)
                .param("purpose", purpose.name())
                .update();
    }
}
