package com.securetransfer.service;

import com.securetransfer.dto.AuthRequest;
import com.securetransfer.dto.AuthResponse;
import com.securetransfer.dto.RegisterRequest;
import com.securetransfer.model.User;

import java.util.Optional;

public interface AuthenticationService {
    Optional<User> authenticate(String username, String password, String deviceId);
    User register(String username, String password, String deviceId);
    void logout(String token);
    boolean validateToken(String token);
} 