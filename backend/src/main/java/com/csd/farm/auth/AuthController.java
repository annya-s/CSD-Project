package com.csd.farm.auth;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final FarmerRepository farmers;
    private final PasswordEncoder passwords;

    public AuthController(FarmerRepository farmers, PasswordEncoder passwords) {
        this.farmers = farmers;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Farmer register(@Valid @RequestBody Registration request) {
        Farmer farmer = new Farmer(UUID.randomUUID(), request.username(), request.displayName().strip());
        farmers.save(farmer, passwords.encode(request.password()));
        return farmer;
    }

    @GetMapping("/me")
    public Farmer me(Principal principal) {
        return farmers.findByUsername(principal.getName()).orElseThrow();
    }

    public record Registration(
            @NotBlank @Pattern(regexp = "[a-z0-9_]{3,40}",
                    message = "Use 3–40 lowercase letters, numbers, or underscores.") String username,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }
}
