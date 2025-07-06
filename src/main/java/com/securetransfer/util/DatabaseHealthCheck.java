package com.securetransfer.util;

import com.securetransfer.model.User;
import com.securetransfer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseHealthCheck {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    
    @Autowired
    private UserRepository userRepository;
    
    public void checkDatabaseHealth() {
        try {
            List<User> users = userRepository.findAll();
            logger.info("Database health check: Found {} users in database", users.size());
            
            if (!users.isEmpty()) {
                logger.info("Existing users: {}", users.stream()
                    .map(User::getUsername)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none"));
            }
            
        } catch (Exception e) {
            logger.error("Database health check failed", e);
        }
    }
    
    public boolean isUserExists(String username) {
        try {
            return userRepository.findByUsername(username).isPresent();
        } catch (Exception e) {
            logger.error("Error checking if user exists: {}", username, e);
            return false;
        }
    }
} 