package com.csd.farm.auth;

import java.security.Principal;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final FarmerRepository farmers;
    private final PasswordEncoder passwords;
    private final AccountTokenService accountTokens;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(FarmerRepository farmers, 
                          PasswordEncoder passwords,
                          AccountTokenService accountTokens,
                          SecurityContextRepository securityContextRepository) {
        this.farmers = farmers;
        this.passwords = passwords;
        this.accountTokens = accountTokens;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Farmer register(@Valid @RequestBody Registration request) {
        Farmer farmer = new Farmer( 
                null,
                request.username(), 
                request.email(),
                false,      // boolean emailVerified
                false,      // boolean twoFactorEnabled
                "FARMER");  // user role is ONLY ever "FARMER"
    
        farmer = farmers.save(farmer, passwords.encode(request.password()));
        accountTokens.issueEmailVerificationCode(farmer);
        return farmer;
    }

    @GetMapping("/me")
    public Farmer me(Principal principal) {
        return farmers.findByUsername(
                principal.getName()).orElseThrow();
    }

    @PostMapping("/verification-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerificationCode(Principal principal) {
        Farmer farmer = farmers.findByUsername(principal.getName()).orElseThrow();

        accountTokens.issueEmailVerificationCode(farmer);
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

    @PostMapping("/username")
    public ResponseEntity<Map<String, String>> changeUsername(
            Principal principal, @RequestBody ChangeUsernameRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

        String newUsername = request.newUsername() == null ? "" : request.newUsername().trim();
        if (!newUsername.matches("[a-z0-9_]{3,40}"))
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Use 3–40 lowercase letters, numbers, or underscores."));
        Farmer current = farmers.findByUsername(principal.getName()).orElseThrow();
        if (newUsername.equals(current.username()))
            return ResponseEntity.badRequest().body(Map.of(
                        "message", "New username cannot be the same as your current username."));
        if (farmers.usernameExists(newUsername))
            return ResponseEntity.badRequest().body(Map.of("message", "Username is already taken."));

        farmers.updateUsername(current.id(), newUsername);

        UserDetails updatedUser = farmers.loadUserByUsername(newUsername);
        Authentication newAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        updatedUser, null, updatedUser.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> changeEmail(
            Principal principal, @RequestBody ChangeEmailRequest request) {

        String newEmail = request.newEmail() == null ? "" : request.newEmail().trim();
        if (newEmail.isBlank() || !newEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a valid email address."));
        Farmer current = farmers.findByUsername(principal.getName()).orElseThrow();
        if (newEmail.equalsIgnoreCase(current.email()))
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "New email cannot be the same as your current email."));
        if (farmers.emailExists(newEmail))
            return ResponseEntity.badRequest().body(Map.of("message", "Email is already in use."));

        farmers.updateEmail(current.id(), newEmail);

        // create&send a new verif token for the new email
        Farmer updatedFarmer = new Farmer(
            current.id(), current.username(), newEmail, false, false, current.role());
        accountTokens.issueEmailVerificationCode(updatedFarmer);

        return ResponseEntity.noContent().build();
    }


    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            Principal principal, @RequestBody ChangePasswordRequest request) {

        if (request.currentPassword() == null || request.currentPassword().isBlank() ||
                request.newPassword() == null || request.newPassword().isBlank())
            return ResponseEntity.badRequest().build();

        Farmer farmer = farmers.findByUsername(principal.getName()).orElseThrow();
        String currentHash = farmers.findPasswordHash(farmer.id());

        if (currentHash == null || !passwords.matches(request.currentPassword(), currentHash))
            return ResponseEntity.badRequest().build();

        farmers.updatePassword(farmer.id(), passwords.encode(request.newPassword()));

        return ResponseEntity.noContent().build();
    }

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}

    @PostMapping("/two-factor")
    public ResponseEntity<Void> setTwoFactor(
            @RequestBody TwoFactorToggle request, Principal principal) {

        Farmer farmer = farmers.findByUsername(principal.getName()).orElseThrow();
        if (request.enabled() && !farmer.emailVerified())
            return ResponseEntity.badRequest().build();

        farmers.setTwoFactorEnabled(farmer.id(), request.enabled());
        return ResponseEntity.noContent().build();
    }



    public record Registration(
            @NotBlank @Pattern(
                    regexp = "[a-z0-9_]{3,40}", 
                    message = "Use 3–40 lowercase letters, numbers, or underscores.") 
            String username,
            @NotBlank @Email @Size(max = 254) String email,
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
    public record ChangeUsernameRequest(String newUsername) {}
    public record ChangeEmailRequest(String newEmail) {}
    public record TwoFactorToggle(
            boolean enabled) {
    }

}


