package com.securetransfer.service.impl;

import com.securetransfer.dto.AuthRequest;
import com.securetransfer.dto.AuthResponse;
import com.securetransfer.dto.RegisterRequest;
import com.securetransfer.exception.ResourceNotFoundException;
import com.securetransfer.exception.UnauthorizedException;
import com.securetransfer.model.User;
import com.securetransfer.repository.UserRepository;
import com.securetransfer.service.AuthenticationService;
import com.securetransfer.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Optional<User> authenticate(String username, String password, String deviceId) {
        logger.info("Attempting authentication for user: {}", username);
        try {
            return userRepository.findByUsername(username)
                    .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                    .map(user -> {
                        user.setLastLogin(LocalDateTime.now());
                        user.setDeviceId(deviceId);
                        return userRepository.save(user);
                    });
        } catch (Exception e) {
            logger.error("Authentication failed for user: {}", username, e);
            throw e;
        }
    }

    @Override
    public User register(String username, String password, String deviceId) {
        logger.info("Attempting registration for user: {}", username);
        try {
            if (userRepository.existsByUsername(username)) {
                logger.warn("Registration failed: Username already exists: {}", username);
                throw new IllegalArgumentException("Username already exists");
            }

            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setDeviceId(deviceId);
            user.setUserToken(UUID.randomUUID().toString());
            user.setActive(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());

            user = userRepository.save(user);
            logger.info("Successfully registered user: {}", username);
            return user;
        } catch (Exception e) {
            logger.error("Registration failed for user: {}", username, e);
            throw e;
        }
    }

    @Override
    public void logout(String token) {
        logger.info("Processing logout request");
        userRepository.findByUserToken(token)
                .ifPresent(user -> {
                    user.setUserToken(null);
                    userRepository.save(user);
                });
        SecurityContextHolder.clearContext();
    }

    @Override
    public boolean validateToken(String token) {
        return userRepository.findByUserToken(token)
                .map(User::isActive)
                .orElse(false);
    }
} 