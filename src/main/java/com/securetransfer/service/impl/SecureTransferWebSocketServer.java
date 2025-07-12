package com.securetransfer.service.impl;

import com.securetransfer.service.WebSocketService.TransferSession;
import com.securetransfer.service.WebSocketService.SenderInfo;
import com.securetransfer.service.WebSocketService.ReceiverInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.securetransfer.util.KeystoreManager;
import java.security.KeyStore;

@Component
public class SecureTransferWebSocketServer extends org.java_websocket.server.WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(SecureTransferWebSocketServer.class);
    private final Map<String, TransferSession> activeSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecureTransferWebSocketServer(@Value("${websocket.port:8445}") int preferredPort) {
        // Bind to all network interfaces (0.0.0.0) to allow external connections
        super(new InetSocketAddress("0.0.0.0", preferredPort));
        int port = getPort();
        logger.info("Initializing WebSocket server on port {} bound to all interfaces", port);
        try {
            KeystoreManager km = new KeystoreManager();
            KeyStore ks = km.loadOrCreateKeystore();
            String password = km.getKeystorePassword();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            setWebSocketFactory(new org.java_websocket.server.DefaultSSLWebSocketServerFactory(sslContext));
        } catch (Exception e) {
            logger.warn("Could not initialize SSL for WebSocket server, running in non-SSL mode: {}", e.getMessage());
        }
    }

    public int getActualPort() {
        return getPort();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String remoteAddress = conn.getRemoteSocketAddress().toString();
        logger.info("New WebSocket connection from: {}", remoteAddress);
        
        // Extract transferCode and role from URI query parameters
        String path = handshake.getResourceDescriptor();
        if (path != null && path.contains("?")) {
            String query = path.substring(path.indexOf("?") + 1);
            Map<String, String> params = parseQueryParams(query);
            
            String transferCode = params.get("code");
            String role = params.get("role");
            
            if (transferCode != null && role != null) {
                boolean isSender = "sender".equalsIgnoreCase(role);
                logger.info("Registering {} WebSocket for transfer code: {} from {}", 
                            role, transferCode, remoteAddress);
                
                // Create the session if it doesn't exist
                activeSessions.computeIfAbsent(transferCode, code -> {
                    logger.info("Creating new transfer session for code: {}", code);
                    // Create temporary empty sender and receiver info
                    SenderInfo sender = new SenderInfo("temp", "unknown", UUID.randomUUID().toString());
                    ReceiverInfo receiver = new ReceiverInfo("temp", "unknown", UUID.randomUUID().toString());
                    return new TransferSession(code, sender, receiver);
                });
                
                // Register WebSocket connection based on role
                registerSession(transferCode, conn, isSender);
                
                // Send confirmation to the client
                try {
                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "connected");
                    response.put("role", role);
                    response.put("transferCode", transferCode);
                    response.put("timestamp", System.currentTimeMillis());
                    conn.send(objectMapper.writeValueAsString(response));
                    
                    logger.info("Sent connection confirmation to {} for transfer code: {}", 
                               role, transferCode);
                } catch (Exception e) {
                    logger.error("Error sending connection confirmation: {}", e.getMessage());
                }
            } else {
                logger.warn("Missing transferCode or role in WebSocket connection: {}", path);
            }
        } else {
            logger.warn("Invalid WebSocket connection path: {}", path);
        }
    }
    
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value);
            }
        }
        return params;
    }
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("WebSocket connection closed: {} (code: {}, reason: {})", conn.getRemoteSocketAddress(), code, reason);
    }
    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Received text message: {}", message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message, Map.class);
            String type = (String) msg.get("type");
            String transferCode = (String) msg.get("transferCode");
            if (type == null || transferCode == null) {
                logger.warn("Invalid message: missing type or transferCode");
                return;
            }
            switch (type) {
                case "register":
                    boolean isSender = Boolean.TRUE.equals(msg.get("isSender"));
                    registerSession(transferCode, conn, isSender);
                    break;
                case "progress":
                    double progress = ((Number) msg.get("progress")).doubleValue();
                    long bytesTransferred = ((Number) msg.get("bytesTransferred")).longValue();
                    logger.info("Progress for {}: {}% ({} bytes)", transferCode, progress * 100, bytesTransferred);
                    break;
                case "complete":
                    boolean success = Boolean.TRUE.equals(msg.get("success"));
                    String error = (String) msg.get("error");
                    logger.info("Transfer complete for {}: success={}, error={}", transferCode, success, error);
                    break;
                default:
                    logger.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Failed to parse or handle message: {}", message, e);
        }
    }
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        logger.debug("Received binary message of {} bytes", message.remaining());
        String transferCode = null;
        boolean isSender = false;
        
        // Find which transfer session and role this connection belongs to
        for (Map.Entry<String, TransferSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().getSenderWebSocket() == conn) {
                transferCode = entry.getKey();
                isSender = true;
                break;
            } else if (entry.getValue().getReceiverWebSocket() == conn) {
                transferCode = entry.getKey();
                isSender = false;
                break;
            }
        }
        
        if (transferCode != null) {
            if (isSender) {
                logger.info("Received binary data from sender for transfer code: {} ({} bytes)", 
                            transferCode, message.remaining());
                sendFileChunk(transferCode, message.array());
            } else {
                logger.info("Received binary data from receiver for transfer code: {} ({} bytes)", 
                            transferCode, message.remaining());
                // Handle response from receiver if needed
            }
        } else {
            logger.warn("Received binary data from unknown connection: {}", conn.getRemoteSocketAddress());
        }
    }
    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error on connection: {}", conn != null ? conn.getRemoteSocketAddress() : "null", ex);
    }
    @Override
    public void onStart() {
        logger.info("WebSocket server started successfully on port {}", getPort());
    }
    public void registerSession(String transferCode, WebSocket conn, boolean isSender) {
        TransferSession session = activeSessions.get(transferCode);
        
        if (session != null) {
            if (isSender) {
                session.setSenderWebSocket(conn);
                logger.info("Sender WebSocket registered for code {}", transferCode);
                
                // If receiver is already connected, notify both parties
                if (session.getReceiverWebSocket() != null) {
                    try {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("type", "peerConnected");
                        notification.put("role", "receiver");
                        notification.put("transferCode", transferCode);
                        notification.put("timestamp", System.currentTimeMillis());
                        conn.send(objectMapper.writeValueAsString(notification));
                        
                        notification.put("role", "sender");
                        session.getReceiverWebSocket().send(objectMapper.writeValueAsString(notification));
                        
                        logger.info("Both peers now connected for transfer code: {}", transferCode);
                    } catch (Exception e) {
                        logger.error("Error sending peer connection notification: {}", e.getMessage());
                    }
                }
            } else {
                session.setReceiverWebSocket(conn);
                logger.info("Receiver WebSocket registered for code {}", transferCode);
                
                // If sender is already connected, notify both parties
                if (session.getSenderWebSocket() != null) {
                    try {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("type", "peerConnected");
                        notification.put("role", "sender");
                        notification.put("transferCode", transferCode);
                        notification.put("timestamp", System.currentTimeMillis());
                        conn.send(objectMapper.writeValueAsString(notification));
                        
                        notification.put("role", "receiver");
                        session.getSenderWebSocket().send(objectMapper.writeValueAsString(notification));
                        
                        logger.info("Both peers now connected for transfer code: {}", transferCode);
                    } catch (Exception e) {
                        logger.error("Error sending peer connection notification: {}", e.getMessage());
                    }
                }
            }
        } else {
            logger.warn("No active session for code {} to register WebSocket", transferCode);
        }
    }
    public void sendFileChunk(String transferCode, byte[] chunk) {
        TransferSession session = activeSessions.get(transferCode);
        if (session != null && session.getReceiverWebSocket() != null) {
            // Check if this is the last chunk by looking for EOF marker
            boolean isLastChunk = false;
            if (chunk.length >= 8) {
                byte[] lastBytes = new byte[8];
                System.arraycopy(chunk, chunk.length - 8, lastBytes, 0, 8);
                String marker = new String(lastBytes);
                isLastChunk = "EOF_MARK".equals(marker);
            }
            
            if (isLastChunk) {
                logger.info("Sending final chunk with EOF marker for transfer code: {} ({} bytes)", 
                    transferCode, chunk.length);
                
                // Send a notification message first
                try {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "finalChunk");
                    notification.put("transferCode", transferCode);
                    notification.put("timestamp", System.currentTimeMillis());
                    session.getReceiverWebSocket().send(objectMapper.writeValueAsString(notification));
                } catch (Exception e) {
                    logger.error("Error sending final chunk notification: {}", e.getMessage());
                }
            }
            
            // Send the actual binary data
            session.getReceiverWebSocket().send(chunk);
            logger.debug("Sent file chunk to receiver for code {} ({} bytes)", transferCode, chunk.length);
            
            // If this was the last chunk, also send a completion message
            if (isLastChunk) {
                try {
                    Map<String, Object> completion = new HashMap<>();
                    completion.put("type", "transferComplete");
                    completion.put("transferCode", transferCode);
                    completion.put("success", true);
                    completion.put("timestamp", System.currentTimeMillis());
                    session.getReceiverWebSocket().send(objectMapper.writeValueAsString(completion));
                    logger.info("Sent transfer completion notification for code: {}", transferCode);
                } catch (Exception e) {
                    logger.error("Error sending transfer completion notification: {}", e.getMessage());
                }
            }
        } else {
            logger.warn("No receiver WebSocket for code {}", transferCode);
        }
    }
} 