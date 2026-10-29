package com.csd.farm.auth;

// [ERD-ALIGNMENT CHANGE] Verification/reset tokens now use public.ACCOUNT_TOKENS.

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountTokenRepository {
    public enum Purpose { EMAIL_VERIFY, PASSWORD_RESET }
    public record UsableToken(UUID id, Long farmerId) { }
    private final JdbcClient jdbc;
    public AccountTokenRepository(JdbcClient jdbc) { this.jdbc=jdbc; }

    public void save(UUID id,Long farmerId,Purpose purpose,String tokenHash,OffsetDateTime expiresAt) {
        jdbc.sql("""
            INSERT INTO public."ACCOUNT_TOKENS" ("Token_ID","User_ID","Purpose","Token_Hash","Expires_At")
            VALUES (:id,:userId,:purpose,:tokenHash,:expiresAt)
            """).param("id",id).param("userId",farmerId).param("purpose",purpose.name())
            .param("tokenHash",tokenHash).param("expiresAt",expiresAt).update();
    }
    public Optional<UsableToken> findUsable(String tokenHash,Purpose purpose) {
        return jdbc.sql("""
            SELECT "Token_ID","User_ID" FROM public."ACCOUNT_TOKENS"
            WHERE "Token_Hash"=:tokenHash AND "Purpose"=:purpose
              AND "Used_At" IS NULL AND "Expires_At">CURRENT_TIMESTAMP
            """).param("tokenHash",tokenHash).param("purpose",purpose.name())
            .query((row,n)->new UsableToken(row.getObject("Token_ID",UUID.class),row.getLong("User_ID"))).optional();
    }
    public void markUsed(UUID tokenId) {
        jdbc.sql("""
            UPDATE public."ACCOUNT_TOKENS" SET "Used_At"=CURRENT_TIMESTAMP
            WHERE "Token_ID"=:id AND "Used_At" IS NULL
            """).param("id",tokenId).update();
    }
    public void invalidateUnused(Long farmerId,Purpose purpose) {
        jdbc.sql("""
            UPDATE public."ACCOUNT_TOKENS" SET "Used_At"=CURRENT_TIMESTAMP
            WHERE "User_ID"=:userId AND "Purpose"=:purpose AND "Used_At" IS NULL
            """)
            .param("userId",farmerId).param("purpose",purpose.name()).update();
    }
}
