package com.csd.farm.auth;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final FarmerRepository farmers;
    private final PasswordEncoder passwords;
    private final AccountTokenService accountTokens;

    public AuthController(FarmerRepository farmers, 
                          PasswordEncoder passwords,
                          AccountTokenService accountTokens) {
        this.farmers = farmers;
        this.passwords = passwords;
        this.accountTokens = accountTokens;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Farmer register(@Valid @RequestBody Registration request) {
        Farmer farmer = new Farmer( 
                UUID.randomUUID(),
                request.username(), 
                request.email(),
                request.displayName().strip(), 
                false);
    
        farmers.save(farmer, passwords.encode(request.password()));
        accountTokens.issueEmailVerificationCode(farmer);
        return farmer;
    }

    @GetMapping("/me")
    public Farmer me(Principal principal) {
        return farmers.findByUsername(
                principal.getName()).orElseThrow();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @RequestBody VerifyEmailRequest request) {

        Farmer farmer = null;
        if (request.username() != null && !request.username().isBlank()) {
            farmer = farmers.findByUsername(
                    request.username().trim()).orElse(null);
        }
        if (farmer == null && request.email() != null
                && !request.email().isBlank()) {

            farmer = farmers.findByEmail(request.email().trim()).orElse(null);
        }        
        if (farmer == null)
            return ResponseEntity.badRequest().build();

        boolean verified = accountTokens.verifyEmailCode(
                farmer.id(), request.code());

        if (!verified) return ResponseEntity.badRequest().build();

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        accountTokens.issuePasswordResetCode(request.email().trim());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        boolean reset = accountTokens.resetPassword(
                request.email(), request.code(), request.password());
        if (!reset) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }



    public record Registration(
            @NotBlank @Pattern(
                    regexp = "[a-z0-9_]{3,40}", 
                    message = "Use 3–40 lowercase letters, numbers, or underscores.") 
            String username,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }
    public record VerifyEmailRequest(
            String username, String email, String code) {
    }
    public record ForgotPasswordRequest(
            @Email @NotBlank String email) {
    }
    public record ResetPasswordRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

}