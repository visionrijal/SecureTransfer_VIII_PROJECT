package com.securetransfer.config;

import com.securetransfer.service.impl.SecureTransferWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class WebSocketConfig {
    @Bean
    public SecureTransferWebSocketServer secureTransferWebSocketServer(@Value("${websocket.port:8445}") int websocketPort) {
        return new SecureTransferWebSocketServer(websocketPort);
    }
} 