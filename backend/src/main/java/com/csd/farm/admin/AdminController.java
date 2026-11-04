package com.csd.farm.admin;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import com.csd.farm.auth.Farmer;
import com.csd.farm.auth.FarmerRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final FarmerRepository farmers;
    private final PasswordEncoder passwordEncoder;

    public AdminController(
            FarmerRepository farmers,
            PasswordEncoder passwordEncoder) {
        this.farmers = farmers;
        this.passwordEncoder = passwordEncoder;
    }

    public record AdminUser(
            Long id,
            String username,
            String email,
            boolean emailVerified,
            boolean twoFactorEnabled,
            String role
    ) {}

    public record PasswordRequest(String password) {}
    public record BooleanRequest(boolean enabled) {}

    @GetMapping("/check")
    public Map<String, String> check(Principal principal) {
        return Map.of("username", principal.getName());
    }

    @GetMapping("/users")
    public List<AdminUser> users() {
        return farmers.findAllUsers().stream()
                .filter(farmer -> !"ADMIN".equals(farmer.role()))
                .map(farmer -> new AdminUser(
                        farmer.id(),
                        farmer.username(),
                        farmer.email(),
                        farmer.emailVerified(),
                        farmer.twoFactorEnabled(),
                        farmer.role()
                ))
                .toList();
    }
    @PostMapping("/users/{id}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @RequestBody PasswordRequest request) {

        if (request.password() == null || request.password().length() < 10) {
            return ResponseEntity.badRequest().build();
        }

        farmers.adminUpdatePassword(
                id,
                passwordEncoder.encode(request.password())
        );

        return ResponseEntity.noContent().build();
    }
    @PostMapping("/users/{id}/unverify")
    public ResponseEntity<Void> unverifyEmail(@PathVariable Long id) {
        farmers.setEmailVerified(id, false);
        farmers.setTwoFactorEnabled(id, false);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/users/{id}/disable2fa")
    public ResponseEntity<Void> setTwoFactor(@PathVariable Long id) {
        farmers.setTwoFactorEnabled(id, false);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = farmers.deleteById(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}