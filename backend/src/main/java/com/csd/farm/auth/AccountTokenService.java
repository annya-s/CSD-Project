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
import org.springframework.transaction.annotation.Transactional;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class AccountTokenService {

    private final AccountTokenRepository tokens;
    private final FarmerRepository farmers;
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder passwords;
    private final JavaMailSender mailSender;

    public AccountTokenService(
            AccountTokenRepository tokens, 
            FarmerRepository farmers, 
            PasswordEncoder passwords,
            JavaMailSender mailSender) {
        this.tokens = tokens;
        this.farmers = farmers;
        this.passwords = passwords;
        this.mailSender = mailSender;
    }

    public void emailSender(String toEmail, String code, String purpose) {
        String subject;
        String body;
        switch(purpose) {
            case "verify" -> {
                subject = "Email Verification Request";
                body = "Use the following code to verify your email :\n" + code 
                         + "\nThis code expires in 24 hours.";
            }
            case "reset" -> {
                subject = "Password Reset Request";
                body = "Use the following code to reset your password :\n" + code 
                         + "\nIf you did not request this code, you may disregard this email.";
            }
            case "login" -> {
                subject = "Login 2FA";
                body = "Use the following code to authenticate your login :\n" + code
                         + "\nIf you did not request this code, please reset your password to secure your account.";
            }
            default -> throw new IllegalArgumentException("Unknown purpose: " + purpose);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("csdfarm.noreply@gmail.com");
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);    
        } catch (MailException e) {
            System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
        }
        
    }

    public void issueEmailVerificationCode(Farmer farmer) {

        tokens.invalidateUnused(farmer.id(),
            AccountTokenRepository.Purpose.EMAIL_VERIFY);

        String code = "%06d".formatted(random.nextInt(1_000_000));

        tokens.save(UUID.randomUUID(), farmer.id(),
                    AccountTokenRepository.Purpose.EMAIL_VERIFY, hash(code), 
                    OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        
        // System.out.println("EMAIL VERIFICATION CODE for + farmer.username() + ": " + code);
        emailSender(farmer.email(), code, "verify");

    }

    @Transactional
    public boolean verifyEmailCode(Long farmerId, String submittedCode) {

        if (submittedCode == null || submittedCode.isBlank()) 
            return false;

        AccountTokenRepository.UsableToken token = tokens.findUsableFor(
                farmerId, hash(submittedCode.trim()), 
                AccountTokenRepository.Purpose.EMAIL_VERIFY)
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

        String code = "%06d".formatted(random.nextInt(1_000_000));

        tokens.save(UUID.randomUUID(), farmer.id(),
                AccountTokenRepository.Purpose.PASSWORD_RESET, hash(code),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        // System.out.println("PASSWORD RESET CODE for "
        //                 + farmer.username() + ": " + code);
        emailSender(farmer.email(), code, "reset");
    }

    @Transactional
    public boolean resetPassword(
            String email, String submittedCode, String newPassword) {

        Farmer farmer = farmers.findByEmail(email).orElse(null);

        if (farmer == null || !farmer.emailVerified() ||
                submittedCode == null || submittedCode.isBlank()) {
            return false;
        }

        AccountTokenRepository.UsableToken token = tokens.findUsableFor(
                farmer.id(), hash(submittedCode.trim()),
                AccountTokenRepository.Purpose.PASSWORD_RESET) 
                .orElse(null);
        if (token == null) return false;

        farmers.updatePassword(farmer.id(), passwords.encode(newPassword));
        tokens.markUsed(token.id());
        return true;
    }

    public void issueLoginCode(Farmer farmer) {

        tokens.invalidateUnused(farmer.id(),
                AccountTokenRepository.Purpose.LOGIN_2FA);

        String code = "%06d".formatted(random.nextInt(1_000_000));

        tokens.save(UUID.randomUUID(), farmer.id(),
                AccountTokenRepository.Purpose.LOGIN_2FA, hash(code),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        emailSender(farmer.email(), code, "login");
    }

    @Transactional
    public boolean verifyLoginCode(Long farmerId, String submittedCode) {
        if (submittedCode == null || submittedCode.isBlank())
            return false;

        AccountTokenRepository.UsableToken token = tokens.findUsableFor(
                farmerId, hash(submittedCode.trim()),
                AccountTokenRepository.Purpose.LOGIN_2FA)
                .orElse(null);
        if (token == null) return false;

        tokens.markUsed(token.id());
        return true;
    }
    
    // "none"  = account has no 2FA, log in
    // "ok"    = code was correct, log in
    // "sent"  = no code yet, one was just emailed
    // "wrong" = code was submitted but is incorrect/expired
    public String loginStep(String username, String code) {
        Farmer farmer = farmers.findByUsername(username).orElseThrow();
        if (!farmer.twoFactorEnabled()) return "none";
        if (code == null || code.isBlank()) {
            issueLoginCode(farmer);
            return "sent";
        }
        return verifyLoginCode(farmer.id(), code) ? "ok" : "wrong";
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

