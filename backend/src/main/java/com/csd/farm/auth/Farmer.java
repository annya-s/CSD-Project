package com.csd.farm.auth;



// Safe to return to the browser: this record never contains a password.
public record Farmer(
        Long id, 
        String username, 
        String email, 
        boolean emailVerified) {
}


