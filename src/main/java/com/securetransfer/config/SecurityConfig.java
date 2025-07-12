package com.securetransfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                authz.requestMatchers("/", "/transfer", "/transfer/**", "/ws/**", "/websocket/**", "/h2-console/**").permitAll()
                     .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin())); // This allows embedding in iframes from same origin (needed for H2 console)
        return http.build();
    }
} 