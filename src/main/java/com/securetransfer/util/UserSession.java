package com.securetransfer.util;

import com.securetransfer.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserSession {

    private static final Logger logger = LoggerFactory.getLogger(UserSession.class);
    private static UserSession instance;
    private User currentUser;

    private UserSession() {
        // Private constructor for singleton
        logger.debug("New UserSession instance created");
    }

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
            logger.debug("Created new UserSession instance");
        }
        return instance;
    }

    public User getCurrentUser() {
        logger.debug("Getting current user: {}", currentUser != null ? currentUser.getUsername() : "null");
        return currentUser;
    }

    public void setCurrentUser(User user) {
        logger.info("Setting current user to: {}", user != null ? user.getUsername() : "null");
        this.currentUser = user;
    }

    public void clearSession() {
        logger.info("Clearing user session. Previous user: {}", currentUser != null ? currentUser.getUsername() : "null");
        this.currentUser = null;
        // Don't clear the instance, just clear the user
        logger.debug("UserSession user cleared");
    }

    public void forceReset() {
        logger.info("Force resetting UserSession instance");
        this.currentUser = null;
        instance = null;
    }

    public boolean isLoggedIn() {
        boolean loggedIn = currentUser != null;
        logger.debug("Checking if logged in: {} (user: {})", loggedIn, currentUser != null ? currentUser.getUsername() : "null");
        return loggedIn;
    }
} 