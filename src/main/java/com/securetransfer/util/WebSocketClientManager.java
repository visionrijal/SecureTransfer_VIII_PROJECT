package com.securetransfer.util;

import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WebSocketClientManager {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientManager.class);

    public enum ConnectionType { DIRECT_LAN, NAT_TRAVERSAL, UPNP }

    public static class ConnectionResult {
        public final ConnectionType type;
        public final WebSocketClient client;
        public ConnectionResult(ConnectionType type, WebSocketClient client) {
            this.type = type;
            this.client = client;
        }
    }

    /**
     * Attempts to connect to the peer using the best available method in order:
     * 1. Direct LAN connection (fastest, lowest latency)
     * 2. NAT traversal with STUN/ICE (works across different networks)
     * 3. UPnP port forwarding (works with compatible routers)
     * 
     * Calls onStatus with status updates for UI (use ToastNotification).
     */
    public CompletableFuture<ConnectionResult> connect(
            String transferCode,
            String role, // "sender" or "receiver"
            List<String> peerLocalAddresses, // LAN IPs to try (if known)
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary
    ) {
        CompletableFuture<ConnectionResult> future = new CompletableFuture<>();
        
        // Check if we should use the enhanced optimal connection method
        boolean useOptimalConnectionStrategy = Boolean.getBoolean("securetransfer.use.optimal.connection");
        
        if (useOptimalConnectionStrategy) {
            onStatus.accept("Using enhanced connection strategy for maximum reliability");
            establishOptimalConnection(
                transferCode, role, onStatus, onError, onOpen, onClose, onMessage, onBinary
            ).thenAccept(optimalClient -> {
                if (optimalClient.isPresent()) {
                    future.complete(new ConnectionResult(ConnectionType.NAT_TRAVERSAL, optimalClient.get()));
                } else {
                    // If optimal strategy fails, use fallback strategies
                    onStatus.accept("Enhanced strategy failed, using legacy connection methods");
                    attemptLegacyConnectionMethods(
                        transferCode, role, peerLocalAddresses, onStatus, onError, 
                        onOpen, onClose, onMessage, onBinary, future);
                }
            });
            return future;
        }
        
        // Otherwise use the original connection strategies
        return attemptLegacyConnectionMethods(
            transferCode, role, peerLocalAddresses, onStatus, onError, 
            onOpen, onClose, onMessage, onBinary, future);
    }
    
    /**
     * Uses the original connection methods for backward compatibility
     */
    private CompletableFuture<ConnectionResult> attemptLegacyConnectionMethods(
            String transferCode,
            String role,
            List<String> peerLocalAddresses,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary,
            CompletableFuture<ConnectionResult> future) {
        
        // 1. Try direct LAN connection first (fastest, lowest latency)
        if (peerLocalAddresses != null && !peerLocalAddresses.isEmpty()) {
            onStatus.accept("Attempting direct LAN connection...");
            CompletableFuture<Optional<WebSocketClient>> lanConnection = tryDirectLanConnections(
                transferCode, role, peerLocalAddresses, onStatus, onError, onOpen, onClose, onMessage, onBinary);
                
            try {
                Optional<WebSocketClient> result = lanConnection.get(3, TimeUnit.SECONDS);
                if (result.isPresent()) {
                    onStatus.accept("Connected via direct LAN");
                    future.complete(new ConnectionResult(ConnectionType.DIRECT_LAN, result.get()));
                    return future;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.warn("Direct LAN connection attempt timed out or failed");
            }
        }
        
        // 2. Try NAT traversal (STUN/ICE) - get public IP and use ICE for connection
        onStatus.accept("Trying NAT traversal with STUN...");
        CompletableFuture<Optional<WebSocketClient>> natConnection = tryNatTraversal(
            transferCode, role, onStatus, onError, onOpen, onClose, onMessage, onBinary);
            
        try {
            Optional<WebSocketClient> result = natConnection.get(30, TimeUnit.SECONDS); // Increased timeout to 30 seconds
            if (result.isPresent()) {
                onStatus.accept("Connected via NAT traversal (STUN/ICE)");
                future.complete(new ConnectionResult(ConnectionType.NAT_TRAVERSAL, result.get()));
                return future; // Return immediately to avoid UPnP attempt
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("NAT traversal connection attempt timed out or failed: {}", e.getMessage());
            // Only proceed to UPnP if NAT traversal definitely failed
        }
        
        // 3. Try UPnP as a last resort only if NAT traversal definitely failed
        // Don't try UPnP if the connection is already established
        if (future.isDone()) {
            return future; // Connection already established, don't try UPnP
        }
        
        onStatus.accept("Trying UPnP port mapping...");
        CompletableFuture<Optional<WebSocketClient>> upnpConnection = tryUPnPConnection(
            transferCode, role, onStatus, onError, onOpen, onClose, onMessage, onBinary);
            
        try {
            Optional<WebSocketClient> result = upnpConnection.get(5, TimeUnit.SECONDS);
            if (result.isPresent()) {
                onStatus.accept("Connected via UPnP");
                future.complete(new ConnectionResult(ConnectionType.UPNP, result.get()));
                return future;
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("UPnP connection attempt timed out or failed");
        }
        
        // 4. All methods failed
        String warning = "Could not establish a connection. Both devices may be behind strict NAT/firewall. Try a different network or VPN.";
        onError.accept(warning);
        future.completeExceptionally(new Exception(warning));
        return future;
    }

    /**
     * Try connecting directly to all provided LAN addresses sequentially
     */
    private CompletableFuture<Optional<WebSocketClient>> tryDirectLanConnections(
            String transferCode,
            String role,
            List<String> peerLocalAddresses,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary) {
            
        CompletableFuture<Optional<WebSocketClient>> result = new CompletableFuture<>();
        
        // Try connecting to addresses sequentially to avoid overwhelming the system
        trySequentialConnections(
            transferCode, role, peerLocalAddresses, 0, 
            onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
        
        return result;
    }
    
    /**
     * Try connections sequentially, stopping when one succeeds
     */
    private void trySequentialConnections(
            String transferCode,
            String role,
            List<String> peerLocalAddresses,
            int currentIndex,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary,
            CompletableFuture<Optional<WebSocketClient>> result) {
        
        // If we've tried all addresses or the result is already completed, stop
        if (currentIndex >= peerLocalAddresses.size() || result.isDone()) {
            if (!result.isDone()) {
                result.complete(Optional.empty());
            }
            return;
        }
        
        String address = peerLocalAddresses.get(currentIndex);
        String ip;
        String port;
        
        // Check if address contains port information (format: "ip:port")
        if (address.contains(":")) {
            String[] parts = address.split(":", 2);
            ip = parts[0];
            port = parts[1];
            
            // Try the specific port
            String url = "ws://" + ip + ":" + port + "/transfer?code=" + transferCode + "&role=" + role;
            logger.info("Trying connection to: {}", url);
            
            try {
                WebSocketClient client = createClient(url, onStatus, onError, onOpen, onClose, onMessage, onBinary);
                
                // Add connection success/failure handlers
                final WebSocketClient finalClient = client;
                client.connect();
                
                // Set up a timeout for this connection attempt
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000); // 2 second timeout per connection
                        
                        // Check if connection is open
                        if (finalClient.isOpen()) {
                            logger.info("Successfully connected to: {}", url);
                            if (!result.isDone()) {
                                result.complete(Optional.of(finalClient));
                            }
                        } else {
                            logger.debug("Connection attempt failed for: {}", url);
                            // Try next address
                            trySequentialConnections(
                                transferCode, role, peerLocalAddresses, currentIndex + 1,
                                onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (!result.isDone()) {
                            result.complete(Optional.empty());
                        }
                    }
                });
                
            } catch (Exception e) {
                logger.debug("Failed to create client for {}: {}", url, e.getMessage());
                // Try next address
                trySequentialConnections(
                    transferCode, role, peerLocalAddresses, currentIndex + 1,
                    onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
            }
        } else {
            // Invalid address format, try next
            trySequentialConnections(
                transferCode, role, peerLocalAddresses, currentIndex + 1,
                onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
        }
    }
    
    /**
     * Try connecting using NAT traversal (STUN/ICE)
     */
    private CompletableFuture<Optional<WebSocketClient>> tryNatTraversal(
            String transferCode,
            String role,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary) {
            
        CompletableFuture<Optional<WebSocketClient>> result = new CompletableFuture<>();
        
        try {
            // Get configuration parameters
            boolean localTestMode = Boolean.getBoolean("securetransfer.local.test");
            String forcedIp = System.getProperty("securetransfer.force.ip");
            boolean tryLocalNetworkFallback = Boolean.getBoolean("securetransfer.try.local.fallback");
            
            // Determine IP strategy (in order of priority)
            // 1. Forced IP (if specified)
            // 2. STUN-discovered public IP
            // 3. Local network IP (as fallback)
            // 4. Localhost (only for local testing)
            
            Optional<String> ipToUse = Optional.empty();
            String connectionMethod = "Unknown";
            
            // First check for forced IP
            if (forcedIp != null && !forcedIp.isEmpty() && NetworkUtils.isValidIpAddress(forcedIp)) {
                ipToUse = Optional.of(forcedIp);
                connectionMethod = "Forced IP";
                logger.info("Using forced IP: {}", forcedIp);
            } 
            // Then try STUN for public IP (unless in local test mode)
            else if (!localTestMode) {
                try {
                    // Use our robust STUN discovery method that tries multiple servers
                    ipToUse = P2PConnectionManager.getRobustExternalIpViaStun(5, 2000);
                    
                    if (ipToUse.isPresent()) {
                        connectionMethod = "STUN-discovered public IP";
                        logger.info("Found public IP via robust STUN discovery: {}", ipToUse.get());
                        onStatus.accept("Found public IP: " + ipToUse.get());
                    } else {
                        logger.warn("Robust STUN discovery failed to find public IP");
                        onStatus.accept("STUN discovery failed to find public IP");
                    }
                } catch (Exception e) {
                    logger.warn("All STUN servers failed: {}", e.getMessage());
                    onStatus.accept("STUN discovery failed: " + e.getMessage());
                }
            }
            
            // Try local network IP as fallback if enabled or if STUN failed
            if (!ipToUse.isPresent() && (tryLocalNetworkFallback || localTestMode)) {
                try {
                    // First try the standard method
                    Optional<String> localIp = NetworkUtils.getLocalIpAddress();
                    if (localIp.isPresent()) {
                        ipToUse = localIp;
                        connectionMethod = "Local network IP";
                        logger.info("Using local network IP as fallback: {}", localIp.get());
                        onStatus.accept("Using local network IP as fallback: " + localIp.get());
                    } else {
                        // If standard method fails, try the more aggressive method
                        Optional<String> bestLocalIpOpt = NetworkUtils.getBestLocalIpAddress();
                        if (bestLocalIpOpt.isPresent()) {
                            String bestLocalIp = bestLocalIpOpt.get();
                            ipToUse = Optional.of(bestLocalIp);
                            connectionMethod = "Best local network IP";
                            logger.info("Using best local network IP as fallback: {}", bestLocalIp);
                            onStatus.accept("Using best local network IP as fallback: " + bestLocalIp);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get local network IP: {}", e.getMessage());
                }
            }
            
          
            if (ipToUse.isPresent()) {
                String ip = ipToUse.get();
                logger.info("Selected connection strategy: {} - IP: {}", connectionMethod, ip);
                onStatus.accept("Using " + connectionMethod + ": " + ip);
                
                // Use port 8445 for WebSocket connections
                final int wsPort = 8445;
                
                // For the receiver, use a direct connection attempt to the sender's IP
                // For the sender, try binding and wait for the receiver to connect
                if ("receiver".equalsIgnoreCase(role)) {
                    // Priority order for receiver to connect:
                    // 1. IP extracted from transfer code (if available)
                    // 2. Forced IP (if specified)
                    // 3. Our own discovered IP (assuming sender has same public IP - could be on same network)
                    
                    // For receiver, we need to connect to the sender
                    // Since we don't have the sender's actual IP, we'll try multiple strategies
                    
                    // Strategy 1: Try using our own public IP (if both devices are behind same NAT)
                    String primaryHost = ip; // Use the discovered public IP
                    logger.info("Receiver will attempt to connect to sender using public IP: {}", primaryHost);
                    
                    // Try multiple connection strategies in parallel for better success rate
                    attemptMultipleConnectionStrategies(transferCode, role, primaryHost, wsPort, onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
                    
                } else {
                    // SENDER: The sender should be the server, so we just need to verify the WebSocket server is running
                    onStatus.accept("Sender ready to receive connections on public IP: " + ip + ":" + wsPort);
                    logger.info("Sender ready to receive connections on public IP {}:{} with transfer code {}", 
                                ip, wsPort, transferCode);
                    
                    // For the sender, we just need to verify the WebSocket server is running and ready
                    // The actual connection will be established when the receiver connects to us
                    try {
                        // Create a simple verification client to check if the server is running
                        String localUrl = "ws://127.0.0.1:" + wsPort + "/transfer?code=" + transferCode + "&role=" + role + "&verify=true";
                        WebSocketClient verificationClient = createClient(localUrl, onStatus, onError, onOpen, onClose, onMessage, onBinary);
                        
                        verificationClient.connectBlocking(3, TimeUnit.SECONDS);
                        if (verificationClient.isOpen()) {
                            logger.info("WebSocket server verified as running and ready");
                            onStatus.accept("WebSocket server ready to accept receiver connections");
                            result.complete(Optional.of(verificationClient));
                        } else {
                            logger.warn("WebSocket server connection failed");
                            onStatus.accept("WebSocket server not ready");
                        result.complete(Optional.empty());
                        }
                    } catch (Exception e) {
                        logger.warn("Error verifying WebSocket server: {}", e.getMessage());
                        onStatus.accept("Error verifying WebSocket server: " + e.getMessage());
                        result.complete(Optional.empty());
                    }
                }
            } else {
                logger.warn("Could not obtain public IP via STUN");
                onStatus.accept("Could not obtain public IP via STUN");
                result.complete(Optional.empty());
            }
        } catch (Exception e) {
            logger.warn("NAT traversal failed: {}", e.getMessage());
            onStatus.accept("NAT traversal failed: " + e.getMessage());
            result.complete(Optional.empty());
        }
        
        return result;
    }
    
    /**
     * Try connecting using UPnP port mapping
     */
    private CompletableFuture<Optional<WebSocketClient>> tryUPnPConnection(
            String transferCode,
            String role,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary) {
            
        CompletableFuture<Optional<WebSocketClient>> result = new CompletableFuture<>();
        
        try {
            if (UPnPManager.isUPnPAvailable()) {
                // Get external IP via UPnP
                Optional<String> externalIp = UPnPManager.getExternalIpAddress();
                if (externalIp.isPresent()) {
                    onStatus.accept("Found external IP via UPnP: " + externalIp.get());
                    
                    // Use WebSocket port 8445 instead of 8081
                    int externalPort = 8445;
                    int internalPort = 8445;
                    
                    boolean mapped = UPnPManager.mapPort(externalPort, internalPort, "TCP", "SecureTransfer")
                        .get(5, TimeUnit.SECONDS);
                        
                    if (mapped) {
                        onStatus.accept("Successfully mapped port " + externalPort + " via UPnP");
                        String url = "ws://" + externalIp.get() + ":" + externalPort + 
                                    "/transfer?code=" + transferCode + "&role=" + role;
                        WebSocketClient client = createClient(url, onStatus, onError, onOpen, onClose, onMessage, onBinary);
                        
                        client.connect();
                        
                        CompletableFuture.runAsync(() -> {
                            try {
                                // Wait up to 5 seconds for connection to establish
                                for (int i = 0; i < 50; i++) {
                                    if (client.isOpen()) {
                                        result.complete(Optional.of(client));
                                        return;
                                    }
                                    Thread.sleep(100);
                                }
                                
                                // If we got here, connection failed
                                client.close();
                                result.complete(Optional.empty());
                                
                                // Clean up port mapping on failure
                                UPnPManager.removePortMapping(externalPort, "TCP");
                            } catch (Exception e) {
                                logger.warn("Error in UPnP connection attempt: {}", e.getMessage());
                                result.complete(Optional.empty());
                                
                                // Clean up port mapping on failure
                                try {
                                UPnPManager.removePortMapping(externalPort, "TCP");
                                } catch (Exception ex) {
                                    logger.warn("Failed to remove UPnP port mapping: {}", ex.getMessage());
                                }
                            }
                        });
                    } else {
                        logger.warn("Failed to map port via UPnP");
                        result.complete(Optional.empty());
                    }
                } else {
                    logger.warn("Could not get external IP via UPnP");
                    result.complete(Optional.empty());
                }
            } else {
                logger.warn("UPnP is not available on this network");
                result.complete(Optional.empty());
            }
        } catch (Exception e) {
            logger.warn("UPnP connection attempt failed: {}", e.getMessage());
            result.complete(Optional.empty());
        }
        
        return result;
    }

    private WebSocketClient createClient(String url,
                                                Consumer<String> onStatus,
                                                Consumer<String> onError,
                                                Consumer<String> onOpen,
                                                Consumer<String> onClose,
                                                Consumer<String> onMessage,
                                                Consumer<ByteBuffer> onBinary) {
        return new WebSocketClient(URI.create(url)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                logger.info("WebSocket opened: {}", url);
                onStatus.accept("WebSocket connection established");
                Platform.runLater(() -> onOpen.accept(url));
            }
            @Override
            public void onMessage(String message) {
                logger.info("WebSocket message: {}", message);
                
                try {
                    // Check if the message is JSON and parse it for better info
                    if (message.startsWith("{") && message.endsWith("}")) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(message);
                        
                        if (json.has("type")) {
                            String type = json.get("type").asText();
                            
                            switch (type) {
                                case "connected":
                                    onStatus.accept("Connection confirmed by server");
                                    break;
                                case "peerConnected":
                                    String role = json.has("role") ? json.get("role").asText() : "unknown";
                                    onStatus.accept("Peer connected: " + role);
                                    break;
                                case "transferComplete":
                                    boolean success = json.has("success") && json.get("success").asBoolean();
                                    if (success) {
                                        onStatus.accept("Transfer completed successfully");
                                    } else {
                                        String errorMsg = json.has("error") ? json.get("error").asText() : "Unknown error";
                                        onStatus.accept("Transfer failed: " + errorMsg);
                                    }
                                    break;
                                case "finalChunk":
                                    onStatus.accept("Receiving final chunk of data...");
                                    break;
                                default:
                                    // Just pass through the raw message
                                    Platform.runLater(() -> onMessage.accept(message));
                            }
                        } else {
                            // Not a recognized JSON message format, just pass through
                            Platform.runLater(() -> onMessage.accept(message));
                        }
                    } else {
                        // Not JSON, just pass through
                        Platform.runLater(() -> onMessage.accept(message));
                    }
                } catch (Exception e) {
                    // If any error parsing, just pass through the original message
                    logger.warn("Error parsing WebSocket message: {}", e.getMessage());
                Platform.runLater(() -> onMessage.accept(message));
                }
            }
            @Override
            public void onMessage(ByteBuffer bytes) {
                logger.info("WebSocket binary message: {} bytes", bytes.remaining());
                Platform.runLater(() -> onBinary.accept(bytes));
            }
            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("WebSocket closed: {} ({} - {})", url, code, reason);
                if (remote) {
                    onStatus.accept("Connection closed by peer: " + reason);
                } else {
                    onStatus.accept("Connection closed: " + reason);
                }
                Platform.runLater(() -> onClose.accept(reason));
            }
            @Override
            public void onError(Exception ex) {
                // Only log as error if it's not a common connection failure
                if (ex instanceof java.net.ConnectException || 
                    ex instanceof java.net.NoRouteToHostException ||
                    ex.getMessage().contains("Connection refused") ||
                    ex.getMessage().contains("No route to host")) {
                    logger.debug("WebSocket connection failed: {}", ex.getMessage());
                } else {
                    logger.error("WebSocket error: {}", ex.getMessage(), ex);
                }
                onStatus.accept("Connection error: " + ex.getMessage());
                Platform.runLater(() -> onError.accept(ex.getMessage()));
            }
        };
    }
    
    /**
     * Gets the sender's IP address for a given transfer code.
     * This method tries to extract the sender's IP from the transfer code or use fallback methods.
     * 
     * @param transferCode The transfer code
     * @return The sender's IP address, or null if not found
     */
    private String getSenderConnectionDetails(String transferCode) {
        logger.info("Looking up sender connection details for transfer code: {}", transferCode);
        
        // 1. Check for forced IP
        String forcedIp = System.getProperty("securetransfer.force.ip");
        if (forcedIp != null && isValidIpAddress(forcedIp)) {
            logger.info("Using forced IP: {}", forcedIp);
            return forcedIp;
        }
        
        // 2. Try to get our own public IP (assuming sender and receiver are on same network)
        try {
            Optional<String> publicIp = P2PConnectionManager.getRobustExternalIpViaStun(3, 2000);
            if (publicIp.isPresent()) {
                logger.info("Using STUN-discovered public IP: {}", publicIp.get());
                return publicIp.get();
            }
        } catch (Exception e) {
            logger.warn("Failed to get public IP for sender lookup: {}", e.getMessage());
        }
        
        // 3. Try local network IPs
        try {
            List<String> localIps = NetworkUtils.getAllLocalIpv4Addresses();
            if (localIps != null && !localIps.isEmpty()) {
                String firstLocalIp = localIps.get(0);
                logger.info("Using local network IP: {}", firstLocalIp);
                return firstLocalIp;
            }
        } catch (Exception e) {
            logger.warn("Failed to get local IPs for sender lookup: {}", e.getMessage());
        }
        
        logger.warn("No sender connection details found for transfer code: {}", transferCode);
        return null;
    }
    
    /**
     * Simple validation for IP address
     */
    private boolean isValidIpAddress(String ip) {
        return NetworkUtils.isValidIpAddress(ip);
    }

    /**
     * Attempts multiple connection strategies in parallel for the receiver to connect to the sender.
     * This improves connection success rates by trying multiple approaches simultaneously.
     *
     * @param transferCode The transfer code
     * @param role The role (sender/receiver)
     * @param primaryHost The primary host to connect to
     * @param port The port to connect to
     * @param onStatus Status callback
     * @param onError Error callback
     * @param onOpen Open callback
     * @param onClose Close callback
     * @param onMessage Message callback
     * @param onBinary Binary message callback
     * @param result The CompletableFuture to complete with the result
     */
    private void attemptMultipleConnectionStrategies(
            String transferCode,
            String role,
            String primaryHost,
            int port,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary,
            CompletableFuture<Optional<WebSocketClient>> result) {
        
        // List to track all client attempts for cleanup
        List<WebSocketClient> clientAttempts = new ArrayList<>();
        
        // Collection of IPs to try, in priority order
        List<String> strategyIps = new ArrayList<>();
        Set<String> uniqueIps = new HashSet<>(); // To avoid duplicates
        
        // 1. First add the primary host (from transfer code or forced IP)
        if (isValidIpAddress(primaryHost)) {
            strategyIps.add(primaryHost);
            uniqueIps.add(primaryHost);
            logger.info("Connection strategy 1: Primary host IP {}", primaryHost);
        }
        
        // 2. Try to get a forced IP (if not already the primary)
        String forcedIp = System.getProperty("securetransfer.force.ip");
        if (forcedIp != null && isValidIpAddress(forcedIp) && !uniqueIps.contains(forcedIp)) {
            strategyIps.add(forcedIp);
            uniqueIps.add(forcedIp);
            logger.info("Connection strategy 2: Forced IP {}", forcedIp);
        }
        
        // 3. Try to get public IP via STUN (if not in local test mode)
        // This should be prioritized for cross-network connections
        boolean localTestMode = Boolean.getBoolean("securetransfer.local.test");
        if (!localTestMode) {
            try {
                // Use our robust STUN discovery method
                Optional<String> publicIp = P2PConnectionManager.getRobustExternalIpViaStun(3, 2000);
                if (publicIp.isPresent() && !uniqueIps.contains(publicIp.get())) {
                    strategyIps.add(publicIp.get());
                    uniqueIps.add(publicIp.get());
                    logger.info("Connection strategy 3: STUN-discovered public IP {}", publicIp.get());
                }
            } catch (Exception e) {
                logger.warn("STUN discovery failed: {}", e.getMessage());
            }
        }
        
        // 4. Try only the most likely local network IPs (limit to 3 to avoid too many attempts)
        // For cross-network connections, local IPs are unlikely to work
        if (localTestMode || strategyIps.size() <= 2) { // Only try local IPs if we don't have many other options
            List<String> localIps = NetworkUtils.getAllLocalIpv4Addresses();
            if (localIps != null && !localIps.isEmpty()) {
                int addedCount = 0;
                for (String localIp : localIps) {
                    if (!uniqueIps.contains(localIp) && addedCount < 3) { // Limit to 3 local IPs
                        strategyIps.add(localIp);
                        uniqueIps.add(localIp);
                        logger.info("Connection strategy 4: Local network IP {}", localIp);
                        addedCount++;
                    }
                }
            }
        }
        
        // 5. For local testing, add localhost as the last resort
        if (localTestMode && !uniqueIps.contains("127.0.0.1")) {
            strategyIps.add("127.0.0.1");
            logger.info("Connection strategy 5: Localhost (test mode)");
        }
        
    // If we have no IPs to try, complete with empty
    if (strategyIps.isEmpty()) {
        logger.warn("No connection strategies available");
        onStatus.accept("No valid connection strategies available. Please check your network configuration.");
        result.complete(Optional.empty());
        return;
    }
    
    logger.info("Attempting {} connection strategies in parallel", strategyIps.size());
    onStatus.accept("Trying " + strategyIps.size() + " connection strategies in parallel for maximum reliability...");
    
    // Track if we've already completed the result
        final AtomicBoolean completed = new AtomicBoolean(false);
        
        // For each IP, create and try a WebSocket connection with multiple ports
        for (int i = 0; i < strategyIps.size(); i++) {
            String ip = strategyIps.get(i);
            int strategyIndex = i + 1;
            
            // Try multiple ports for each IP (reduced from 3 to 2 for faster connection)
            String[] portsToTry = {"8445", "8446"};
            for (String portToTry : portsToTry) {
                String url = "ws://" + ip + ":" + portToTry + "/transfer?code=" + transferCode + "&role=" + role;
                            try {
                    WebSocketClient client = createClient(url, 
                        // Custom status handler to include strategy number and port
                        (status) -> onStatus.accept("Strategy " + strategyIndex + " (" + ip + ":" + portToTry + "): " + status),
                        onError, onOpen, onClose, onMessage, onBinary);
                    
                    clientAttempts.add(client);
                    client.connect();
                    
                    // Check connection status in a separate thread
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Wait up to 5 seconds for this connection attempt
                            for (int j = 0; j < 50; j++) {
                                if (client.isOpen()) {
                                    // If this is the first successful connection, complete the result
                                    if (completed.compareAndSet(false, true)) {
                                        logger.info("Strategy {} ({}:{}) succeeded", strategyIndex, ip, portToTry);
                                        onStatus.accept("Connected using strategy " + strategyIndex + " (" + ip + ":" + portToTry + ")");
                                        result.complete(Optional.of(client));
                                        
                                        // Close all other client attempts
                                        for (WebSocketClient otherClient : clientAttempts) {
                                            if (otherClient != client && otherClient.isOpen()) {
                                                try {
                                                    otherClient.close();
                                                } catch (Exception e) {
                                                    // Ignore errors when closing other clients
                                                }
                                            }
                                        }
                                    }
                                    return;
                                }
                                Thread.sleep(100);
                            }
                            
                            // This strategy failed, try to close the client
                            if (client.isOpen()) {
                                client.close();
                            }
                            
                            // Only log failure if we haven't succeeded yet (to reduce spam)
                            if (!completed.get()) {
                                logger.debug("Strategy {} ({}:{}) failed", strategyIndex, ip, portToTry);
                            }
                        } catch (Exception e) {
                            if (!completed.get()) {
                                logger.debug("Error in connection strategy {}: {}", strategyIndex, e.getMessage());
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    logger.warn("Error setting up connection strategy {} ({}:{}): {}", strategyIndex, ip, portToTry, e.getMessage());
                }
            }
        }
        
        // Set a timeout to complete with empty if no strategies succeed
        CompletableFuture.delayedExecutor(8000, TimeUnit.MILLISECONDS).execute(() -> {
            if (completed.compareAndSet(false, true)) {
                logger.warn("All connection strategies failed");
                onStatus.accept("All connection strategies failed. Please verify both devices have network connectivity and are not behind restrictive firewalls.");
                
                // Close all client attempts
                for (WebSocketClient client : clientAttempts) {
                    try {
                        if (client.isOpen()) {
                            client.close();
                        }
                    } catch (Exception e) {
                        // Ignore errors when closing clients
                    }
                }
                
                result.complete(Optional.empty());
            }
        });
    }
    
    /**
     * Attempts to establish the most reliable connection possible using all available strategies.
     * This method is an enhanced version of the connection logic that uses more sophisticated
     * connection testing and fallback mechanisms.
     *
     * @param transferCode The transfer code for this connection
     * @param role The role (sender/receiver)
     * @param onStatus Status callback
     * @param onError Error callback
     * @param onOpen Open callback
     * @param onClose Close callback
     * @param onMessage Message callback
     * @param onBinary Binary message callback
     * @return A future with the most reliable connection that could be established
     */
    public CompletableFuture<Optional<WebSocketClient>> establishOptimalConnection(
            String transferCode,
            String role,
            Consumer<String> onStatus,
            Consumer<String> onError,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onMessage,
            Consumer<ByteBuffer> onBinary) {
            
        CompletableFuture<Optional<WebSocketClient>> result = new CompletableFuture<>();
        onStatus.accept("Finding optimal connection path...");
        
        // Step 1: Gather all possible IP addresses to try
        List<String> candidateIps = new ArrayList<>();
        
        // Add any IP embedded in the transfer code
        String codeIp = getSenderConnectionDetails(transferCode);
        if (codeIp != null) {
            candidateIps.add(codeIp);
            onStatus.accept("Found IP in transfer code: " + codeIp);
        }
        
        // Add forced IP if specified
        String forcedIp = System.getProperty("securetransfer.force.ip");
        if (forcedIp != null && !forcedIp.isEmpty() && NetworkUtils.isValidIpAddress(forcedIp)) {
            candidateIps.add(forcedIp);
            onStatus.accept("Using forced IP: " + forcedIp);
        }
        
        // Add public IP from STUN (if not in local test mode)
        boolean localTestMode = Boolean.getBoolean("securetransfer.local.test");
        if (!localTestMode) {
            try {
                Optional<String> publicIp = P2PConnectionManager.getRobustExternalIpViaStun(3, 2000);
                if (publicIp.isPresent()) {
                    candidateIps.add(publicIp.get());
                    onStatus.accept("Found public IP via STUN: " + publicIp.get());
                }
            } catch (Exception e) {
                logger.warn("STUN discovery failed: {}", e.getMessage());
            }
        }
        
        // Add all local network IPs
        List<String> localIps = NetworkUtils.getAllLocalIpv4Addresses();
        candidateIps.addAll(localIps);
        onStatus.accept("Added " + localIps.size() + " local network IPs");
        
        // In test mode, also add localhost
        if (localTestMode) {
            candidateIps.add("127.0.0.1");
            onStatus.accept("Added localhost for testing");
        }
        
        // Remove duplicates and prioritize
        List<String> uniqueIps = new ArrayList<>(new HashSet<>(candidateIps));
        List<String> prioritizedIps = NetworkUtils.prioritizeIpAddresses(uniqueIps);
        
        onStatus.accept("Testing " + prioritizedIps.size() + " potential connection paths");
        logger.info("Prioritized connection candidates: {}", prioritizedIps);
        
        // Step 2: Test each IP to find the most reliable one
        final int port = 8445; // WebSocket port
        
        // First, do a fast parallel port check to eliminate obviously unreachable endpoints
        NetworkUtils.testNetworkAddressesInParallel(prioritizedIps, port, 1000)
            .thenAccept(bestIp -> {
                if (bestIp.isPresent()) {
                    // We found a working IP, now create the WebSocket connection
                    onStatus.accept("Found optimal connection path: " + bestIp.get() + ":" + port);
                    String url = "ws://" + bestIp.get() + ":" + port + "/transfer?code=" + transferCode + "&role=" + role;
                    WebSocketClient client = createClient(url, onStatus, onError, onOpen, onClose, onMessage, onBinary);
                    
                    try {
                        client.connect();
                        
                        // Give it some time to establish
                        new Thread(() -> {
                            try {
                                for (int i = 0; i < 50; i++) { // 5 seconds max
                                    if (client.isOpen()) {
                                        result.complete(Optional.of(client));
                                        return;
                                    }
                                    Thread.sleep(100);
                                }
                                
                                // If it failed to open, try the multi-strategy approach
                                if (!result.isDone()) {
                                    onStatus.accept("Optimal path failed, trying comprehensive approach");
                                    attemptMultipleConnectionStrategies(
                                        transferCode, role, prioritizedIps.get(0), port, 
                                        onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
                                }
                            } catch (Exception e) {
                                logger.warn("Error waiting for connection: {}", e.getMessage());
                                if (!result.isDone()) {
                                    result.complete(Optional.empty());
                                }
                            }
                        }).start();
                    } catch (Exception e) {
                        logger.warn("Error connecting to optimal path: {}", e.getMessage());
                        // If it failed, try the multi-strategy approach
                        attemptMultipleConnectionStrategies(
                            transferCode, role, prioritizedIps.get(0), port, 
                            onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
                    }
                } else {
                    // No working IP found in fast test, try the comprehensive approach
                    onStatus.accept("No optimal path found, trying comprehensive approach");
                    attemptMultipleConnectionStrategies(
                        transferCode, role, prioritizedIps.isEmpty() ? null : prioritizedIps.get(0), port, 
                        onStatus, onError, onOpen, onClose, onMessage, onBinary, result);
                }
            });
        
        return result;
    }
}