package com.csd.farm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import com.csd.farm.auth.AccountTokenService;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
		// PBKDF2 handles long passwords without bcrypt's 72-byte limit.
		return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}
    @Bean
    public SecurityFilterChain securityFilterChain(
			HttpSecurity http, AccountTokenService accTokens) throws Exception {
	http
		.authorizeHttpRequests(auth -> auth
			// Page shells are public; their data still requires an authenticated API request.
			.requestMatchers("/", "/index.html", "/error", 
					"/api.js", "/login.html", "/login.js", "/account.html", "/account.js",
					"/styles.css", "/favicon.svg", "/icons.svg").permitAll()
			.requestMatchers(HttpMethod.GET, 
					"/api/auth/csrf").permitAll()
			.requestMatchers(HttpMethod.POST, 
					"/api/auth/register", "/api/auth/login", "/api/auth/verify-email", 
					"/api/auth/forgot-password", "/api/auth/reset-password").permitAll()

			.requestMatchers(
					"/admin.html", "/admin.js",	"/api/admin", "/api/admin/**"
			).hasRole("ADMIN")

			.requestMatchers(
					"/dashboard.html", "/dashboard.js", "/api/dashboard", "/api/dashboard/**",
					"/crop-details.html", "/crop-details.js", "/api/crops",	"/api/crops/**", 
					"/api/crop-types", "/api/crop-types/**", "/new-entry.html", "/new-entry.js"
			).hasRole("FARMER")

			.anyRequest().authenticated())
		
		// Spring Security keeps CSRF protection and rotates the session on login.
		.formLogin(login -> login
			.loginProcessingUrl("/api/auth/login")
			// .successHandler((request, response, authentication) -> response.setStatus(204))
			.successHandler((request, response, authentication) -> {
                String step = accTokens.loginStep(
                        authentication.getName(), request.getParameter("code"));

                // user doesn't use 2fa, or inputted correct code
                if (step.equals("none") || step.equals("ok")) {
                    response.setStatus(204);
                    return;
                }
                // missing or wrong code : undo the login Spring just made.
                SecurityContextHolder.clearContext();
                request.getSession().removeAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                response.setContentType("application/json");
                if (step.equals("sent")) {
                    response.setStatus(200);
                    response.getWriter().write("{\"twoFactorRequired\":true}");
                } else {
                    response.setStatus(401);
                    response.getWriter().write("{\"message\":\"That code is incorrect or expired.\"}");
                }
            })
			.failureHandler((request, response, exception) -> {
			    response.setStatus(401);
			    response.setContentType("application/json");
			    response.getWriter().write("{\"message\":\"Invalid username or password.\"}");
			}))
		.logout(logout -> logout
			.logoutUrl("/api/auth/logout")
			.deleteCookies("JSESSIONID")
			.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
		.exceptionHandling(errors -> errors
			.authenticationEntryPoint((request, response, exception) -> {
				if (request.getServletPath().endsWith(".html")) {
					response.sendRedirect("/login.html");
					return;
				}
				response.setStatus(401);
			    response.setContentType("application/json");
			    response.getWriter().write("{\"message\":\"Please log in to continue.\"}");
			})
			.accessDeniedHandler((request, response, exception) -> {
			    response.setStatus(403);
			    response.setContentType("application/json");
			    response.getWriter().write("{\"message\":\"Your session token has expired. Refresh and try again.\"}");
			}))
		.headers(headers -> headers
			.contentSecurityPolicy(policy -> policy.policyDirectives(
				"default-src 'self' https://unpkg.com; "
				+ "img-src 'self' data: https://tile.openstreetmap.org https://unpkg.com https://stadiamaps.com "
				+ "style-src 'self' https://unpkg.com; "
				+ "script-src 'self' https://unpkg.com; "
				+ "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));

	return http.build();
    }
}

