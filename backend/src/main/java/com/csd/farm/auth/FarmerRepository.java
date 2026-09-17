package com.csd.farm.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class FarmerRepository implements UserDetailsService {

    private final JdbcClient jdbc;

    public FarmerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Farmer farmer, String passwordHash) {
        jdbc.sql("""
                INSERT INTO farm.farmer_account (id, username, display_name, password_hash)
                VALUES (:id, :username, :displayName, :passwordHash)
                """)
                .param("id", farmer.id())
                .param("username", farmer.username())
                .param("displayName", farmer.displayName())
                .param("passwordHash", passwordHash)
                .update();
    }

    public Optional<Farmer> findByUsername(String username) {
        return jdbc.sql("""
                SELECT id, username, display_name
                FROM farm.farmer_account WHERE username = :username
                """)
                .param("username", username)
                .query((row, number) -> new Farmer(
                        row.getObject("id", UUID.class),
                        row.getString("username"),
                        row.getString("display_name")))
                .optional();
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbc.sql("""
                SELECT username, password_hash
                FROM farm.farmer_account WHERE username = :username
                """)
                .param("username", username)
                .query((row, number) -> User.withUsername(row.getString("username"))
                        .password(row.getString("password_hash"))
                        .roles("FARMER")
                        .build())
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."));
    }
}
