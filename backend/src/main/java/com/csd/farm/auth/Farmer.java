package com.csd.farm.auth;

import java.util.UUID;

// Safe to return to the browser: this record never contains a password.
public record Farmer(UUID id, String username, String displayName) {
}
