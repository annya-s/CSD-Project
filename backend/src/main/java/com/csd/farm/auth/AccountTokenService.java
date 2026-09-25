package com.csd.farm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountTokenService {

    private final AccountTokenRepository tokens;
    private final FarmerRepository farmers;
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder passwords;

    public AccountTokenService(
            AccountTokenRepository tokens, 
            FarmerRepository farmers, 
            PasswordEncoder passwords) {
        this.tokens = tokens;
        this.farmers = farmers;
        this.passwords = passwords;
    }

    // Current delivery behavior: print the code in the backend terminal.
    // Later delivery behavior: send this same code by email.
    public void issueEmailVerificationCode(Farmer farmer) {
        
        String code = "%06d".formatted(
                random.nextInt(1_000_000));

        OffsetDateTime expiresAt = OffsetDateTime.now(
                ZoneOffset.UTC).plusHours(24);

        tokens.save(UUID.randomUUID(), farmer.id(),
                    AccountTokenRepository.Purpose.EMAIL_VERIFY,
                    hash(code), expiresAt);
        
        // TODO
        // REPLACE WITH ACTUAL EMAIL
        System.out.println("EMAIL VERIFICATION CODE for "
                            + farmer.username() + ": " + code);
    }

    public boolean verifyEmailCode(
            UUID farmerId, String submittedCode) {

        if (submittedCode == null || submittedCode.isBlank())
            return false;

        AccountTokenRepository.UsableToken token =
                tokens.findUsable(
                        hash(submittedCode.trim()),
                        AccountTokenRepository.Purpose.EMAIL_VERIFY)
                        .filter(candidate -> 
                                candidate.farmerId().equals(farmerId))
                        .orElse(null);
        if (token == null) return false;

        farmers.markEmailVerified(farmerId);
        tokens.markUsed(token.id());
        return true;
    }

    public void issuePasswordResetCode(String email) {
        Farmer farmer = farmers.findByEmail(email).orElse(null);

        if (farmer == null) return;
        tokens.invalidateUnused(farmer.id(),
                AccountTokenRepository.Purpose.PASSWORD_RESET);

        String code = "%06d".formatted(
                random.nextInt(1_000_000));

        tokens.save(UUID.randomUUID(), farmer.id(),
                AccountTokenRepository.Purpose.PASSWORD_RESET, hash(code),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        // TODO
        // REPLACE WITH ACTUAL EMAIL
        System.out.println("PASSWORD RESET CODE for "
                        + farmer.username() + ": " + code);
    }

    public boolean resetPassword(
            String email, String submittedCode, String newPassword) {

        Farmer farmer = farmers.findByEmail(email).orElse(null);

        if (farmer == null || !farmer.emailVerified() ||
                submittedCode == null || submittedCode.isBlank()) {
            return false;
        }

        AccountTokenRepository.UsableToken token =
                tokens.findUsable(
                        hash(submittedCode.trim()),
                        AccountTokenRepository.Purpose.PASSWORD_RESET)
                        .filter(candidate ->
                                candidate.farmerId().equals(farmer.id()))
                        .orElse(null);
        if (token == null) return false;

        farmers.updatePassword(farmer.id(), passwords.encode(newPassword));
        tokens.markUsed(token.id());
        return true;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.", exception);
        }
    }
}