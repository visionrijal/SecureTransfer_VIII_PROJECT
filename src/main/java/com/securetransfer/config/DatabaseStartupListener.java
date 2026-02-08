package com.securetransfer.config;

import com.securetransfer.util.DatabaseHealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStartupListener {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseStartupListener.class);
    
    @Autowired
    private DatabaseHealthCheck databaseHealthCheck;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application started - checking database health...");
        databaseHealthCheck.checkDatabaseHealth();
    }
} 