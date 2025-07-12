package com.securetransfer.util;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.ice4j.security.LongTermCredential;

/**
 * P2P connection manager that uses various techniques to establish direct connections
 * without relying on a relay server.
 */
public class P2PConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(P2PConnectionManager.class);
    
    // Public STUN servers - these are free to use
    private static final String[] PUBLIC_STUN_SERVERS = {
        "stun.l.google.com:19302",
        "stun1.l.google.com:19302",
        "stun2.l.google.com:19302",
        "stun3.l.google.com:19302",
        "stun4.l.google.com:19302",
        "stun.stunprotocol.org:3478",
        "stun.ekiga.net:3478",
        "stun.ideasip.com:3478",
        "stun.voiparound.com:3478",
        "stun.voipbuster.com:3478",
        "stun.voipstunt.com:3478",
        "stun.voxgratia.org:3478",
        "stun.freeswitch.org:3478",
        "stun.ipfire.org:3478"
    };

    // Public TURN servers with static credentials
    private static final TurnServer[] PUBLIC_TURN_SERVERS = {
        new TurnServer("freestun.net", 3478, "free", "free"),
        new TurnServer("turn.bistri.com", 80, "homeo", "homeo"),
        new TurnServer("turn.anyfirewall.com", 443, "webrtc", "webrtc"),
        new TurnServer("numb.viagenie.ca", 3478, "webrtc@live.com", "muazkh")
    };

    private static class TurnServer {
        final String host;
        final int port;
        final String username;
        final String credential;
        TurnServer(String host, int port, String username, String credential) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.credential = credential;
        }
    }
    
    /**
     * Gets a random STUN server from the list of public servers
     */
    private static String getRandomStunServer() {
        int index = ThreadLocalRandom.current().nextInt(PUBLIC_STUN_SERVERS.length);
        return PUBLIC_STUN_SERVERS[index];
    }
    
    /**
     * Gets a shuffled array of STUN servers for more robust connection attempts
     */
    public static String[] getShuffledStunServers() {
        String[] shuffledServers = PUBLIC_STUN_SERVERS.clone();
        shuffleArray(shuffledServers);
        return shuffledServers;
    }
    
    /**
     * Get the external IP address using STUN protocol
     * @return The external IP address, or empty if unable to determine
     */
    public static Optional<String> getExternalIpViaStun() {
        try {
            String stunServer = getRandomStunServer();
            String[] parts = stunServer.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            
            Agent agent = new Agent();
            agent.setControlling(true);
            
            StunCandidateHarvester stunHarvester = new StunCandidateHarvester(
                    new TransportAddress(host, port, Transport.UDP));
                    
            agent.addCandidateHarvester(stunHarvester);
            IceMediaStream stream = agent.createMediaStream("data");
            
            // Set the port properties to valid values with validation
            int minPort = parsePort(System.getProperty("p2p.ice.min.port"), 5000);
            int maxPort = parsePort(System.getProperty("p2p.ice.max.port"), 5100);
            int preferredPort = parsePort(System.getProperty("p2p.ice.preferred.port"), minPort);
            if (minPort < 1024 || minPort > 65535) {
                logger.warn("Invalid minPort {}. Using default 5000.", minPort);
                minPort = 5000;
            }
            if (maxPort < minPort || maxPort > 65535) {
                logger.warn("Invalid maxPort {}. Using default 5100.", maxPort);
                maxPort = 5100;
            }
            if (preferredPort < minPort || preferredPort > maxPort) {
                logger.warn("Invalid preferredPort {}. Using minPort {} as preferred.", preferredPort, minPort);
                preferredPort = minPort;
            }
            logger.info("Using ICE port range: {}-{}, preferred port: {}", minPort, maxPort, preferredPort);
            // Important: Set preferred port before minPort and maxPort
            System.setProperty("org.ice4j.ice.harvest.PREFERRED_PORT", String.valueOf(preferredPort));
            System.setProperty("org.ice4j.ice.MIN_PORT", String.valueOf(minPort));
            System.setProperty("org.ice4j.ice.MAX_PORT", String.valueOf(maxPort));
            
            // Create a component with our own specified port
            try {
                // Create the component with preferred port as first parameter
                agent.createComponent(stream, preferredPort, minPort, maxPort);
                agent.startConnectivityEstablishment();
            } catch (Exception e) {
                logger.error("Error creating ICE component: {}", e.getMessage());
                return Optional.empty();
            }
            
            for (LocalCandidate candidate : stream.getComponent(1).getLocalCandidates()) {
                if (candidate.getType().toString().equals("srflx")) {
                    return Optional.of(candidate.getTransportAddress().getAddress().getHostAddress());
                }
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error determining external IP via STUN", e);
            return Optional.empty();
        }
    }
    
    /**
     * Get the external IP address using a specific STUN server
     * @param stunServer The STUN server to use in format host:port
     * @return The external IP address, or empty if unable to determine
     */
    public static Optional<String> getExternalIpViaStun(String stunServer) {
        try {
            String[] parts = stunServer.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            
            Agent agent = new Agent();
            agent.setControlling(true);
            
            StunCandidateHarvester stunHarvester = new StunCandidateHarvester(
                    new TransportAddress(host, port, Transport.UDP));
                    
            agent.addCandidateHarvester(stunHarvester);
            IceMediaStream stream = agent.createMediaStream("data");
            
            // Set the port properties to valid values with validation
            int minPort = parsePort(System.getProperty("p2p.ice.min.port"), 5000);
            int maxPort = parsePort(System.getProperty("p2p.ice.max.port"), 5100);
            int preferredPort = parsePort(System.getProperty("p2p.ice.preferred.port"), minPort);
            if (minPort < 1024 || minPort > 65535) {
                logger.warn("Invalid minPort {}. Using default 5000.", minPort);
                minPort = 5000;
            }
            if (maxPort < minPort || maxPort > 65535) {
                logger.warn("Invalid maxPort {}. Using default 5100.", maxPort);
                maxPort = 5100;
            }
            if (preferredPort < minPort || preferredPort > maxPort) {
                logger.warn("Invalid preferredPort {}. Using minPort {} as preferred.", preferredPort, minPort);
                preferredPort = minPort;
            }
            
            // Create a component with our own specified port
            try {
                // Create the component with preferred port as first parameter
                agent.createComponent(stream, preferredPort, minPort, maxPort);
                agent.startConnectivityEstablishment();
            } catch (Exception e) {
                logger.error("Error creating ICE component: {}", e.getMessage());
                return Optional.empty();
            }
            
            for (LocalCandidate candidate : stream.getComponent(1).getLocalCandidates()) {
                if (candidate.getType().toString().equals("srflx")) {
                    return Optional.of(candidate.getTransportAddress().getAddress().getHostAddress());
                }
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error determining external IP via STUN server {}", stunServer, e);
            return Optional.empty();
        }
    }
    
    /**
     * Attempts to discover the best available external IP and port
     * using multiple methods (STUN, UPnP, etc.)
     * 
     * @param internalPort The internal port to map if using UPnP
     * @return Connection details including IP and port information
     */
    public static CompletableFuture<ConnectionDetails> discoverConnectionDetails(int internalPort) {
        return CompletableFuture.supplyAsync(() -> {
            ConnectionDetails details = new ConnectionDetails();
            
            // First get the local IP to ensure we have it for fallback
            Optional<String> localIp = NetworkUtils.getLocalIpAddress();
            if (localIp.isPresent()) {
                details.setLocalIp(localIp.get());
            } else {
                // If getLocalIpAddress fails, try our more aggressive method
                Optional<String> bestLocalIp = NetworkUtils.getBestLocalIpAddress();
                if (bestLocalIp.isPresent()) {
                    details.setLocalIp(bestLocalIp.get());
                } else {
                    // Last resort fallback to localhost
                    details.setLocalIp("127.0.0.1");
                    logger.warn("Could not determine any local IP, falling back to localhost");
                }
            }
            
            // Try STUN first (preferred for NAT traversal) with our robust method
            logger.info("Attempting to discover connection details via STUN");
            Optional<String> externalIpStun = getRobustExternalIpViaStun(5, 3000);
            if (externalIpStun.isPresent()) {
                details.setExternalIp(externalIpStun.get());
                details.setDiscoveryMethod(DiscoveryMethod.STUN);
                logger.info("Discovered external IP {} via STUN", externalIpStun.get());
                
                // We don't know the external port, so we'll use the internal port
                // This might work if the user has port forwarding set up manually
                details.setExternalPort(internalPort);
                return details;
            }
            
            // If STUN fails, try UPnP as fallback
            if (UPnPManager.isUPnPAvailable()) {
                logger.info("STUN failed, attempting to use UPnP for port mapping");
                
                try {
                    // Get external IP address via UPnP
                    Optional<String> externalIp = UPnPManager.getExternalIpAddress();
                    if (externalIp.isPresent()) {
                        details.setExternalIp(externalIp.get());
                        details.setDiscoveryMethod(DiscoveryMethod.UPNP);
                        
                        // Map a port
                        int externalPort = internalPort;
                        boolean mappingResult = UPnPManager.mapPort(
                            externalPort, internalPort, "TCP", "SecureTransfer")
                            .get(); // Wait for the result
                            
                        if (mappingResult) {
                            details.setExternalPort(externalPort);
                            details.setPortMapped(true);
                            logger.info("Successfully mapped port {} to {} via UPnP", 
                                        externalPort, internalPort);
                            return details;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error using UPnP for connection details", e);
                }
            } else {
                // As a last resort, use the local IP
                logger.warn("Could not determine external IP, falling back to local IP");
                UPnPManager.getLocalIpAddress().ifPresent(ip -> {
                    details.setExternalIp(ip);
                    details.setLocalIp(ip);
                    details.setDiscoveryMethod(DiscoveryMethod.LOCAL_ONLY);
                });
                details.setExternalPort(internalPort);
            }
            
            return details;
        });
    }
    
    /**
     * Attempts to establish a direct P2P connection
     * 
     * @param targetIp The target IP address
     * @param targetPort The target port
     * @param timeoutMs Connection timeout in milliseconds
     * @return True if connection successful, false otherwise
     */
    public static boolean testDirectConnection(String targetIp, int targetPort, int timeoutMs) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetIp, targetPort), timeoutMs);
            boolean isConnected = socket.isConnected();
            socket.close();
            return isConnected;
        } catch (Exception e) {
            logger.warn("Failed to establish direct connection to {}:{}: {}", 
                       targetIp, targetPort, e.getMessage());
            return false;
        }
    }
    
    public static CompletableFuture<IceNegotiationResult> createAndNegotiateIceAgent(String remoteIceInfo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean controlling = Math.random() > 0.5;
                Agent agent = new Agent();
                agent.setControlling(controlling);

                // Add all STUN servers
                for (String stunServer : PUBLIC_STUN_SERVERS) {
                    String[] parts = stunServer.split(":");
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    StunCandidateHarvester stunHarvester = new StunCandidateHarvester(
                        new TransportAddress(host, port, Transport.UDP));
                    agent.addCandidateHarvester(stunHarvester);
                }

                // Add all TURN servers
                for (TurnServer turn : PUBLIC_TURN_SERVERS) {
                    try {
                        TurnCandidateHarvester turnHarvester = new TurnCandidateHarvester(
                            new TransportAddress(turn.host, turn.port, Transport.UDP),
                            new LongTermCredential(turn.username, turn.credential)
                        );
                        agent.addCandidateHarvester(turnHarvester);
                    } catch (Exception e) {
                        logger.warn("Failed to add TURN harvester for {}:{} - {}", turn.host, turn.port, e.getMessage());
                    }
                }

                IceMediaStream stream = agent.createMediaStream("data");
                int minPort = parsePort(System.getProperty("p2p.ice.min.port"), 5000);
                int maxPort = parsePort(System.getProperty("p2p.ice.max.port"), 5100);
                int preferredPort = parsePort(System.getProperty("p2p.ice.preferred.port"), minPort);
                if (minPort < 1024 || minPort > 65535) {
                    logger.warn("Invalid minPort {}. Using default 5000.", minPort);
                    minPort = 5000;
                }
                if (maxPort < minPort || maxPort > 65535) {
                    logger.warn("Invalid maxPort {}. Using default 5100.", maxPort);
                    maxPort = 5100;
                }
                if (preferredPort < minPort || preferredPort > maxPort) {
                    logger.warn("Invalid preferredPort {}. Using minPort {} as preferred.", preferredPort, minPort);
                    preferredPort = minPort;
                }
                logger.info("Using ICE port range for negotiation: {}-{}, preferred port: {}", minPort, maxPort, preferredPort);
                // Important: Set preferred port before minPort and maxPort
                System.setProperty("org.ice4j.ice.harvest.PREFERRED_PORT", String.valueOf(preferredPort));
                System.setProperty("org.ice4j.ice.ALLOWED_INTERFACES", "*");
                System.setProperty("org.ice4j.ice.MIN_PORT", String.valueOf(minPort));
                System.setProperty("org.ice4j.ice.MAX_PORT", String.valueOf(maxPort));
                agent.createComponent(stream, preferredPort, minPort, maxPort);
                if (remoteIceInfo != null && !remoteIceInfo.isEmpty()) {
                    logger.info("Remote ICE info provided, would process it here");
                }
                agent.startConnectivityEstablishment();
                boolean iceSucceeded = agent.getState() == IceProcessingState.COMPLETED;
                if (!iceSucceeded) {
                    for (int i = 0; i < 100 && !iceSucceeded; i++) {
                        Thread.sleep(100);
                        iceSucceeded = agent.getState() == IceProcessingState.COMPLETED;
                    }
                }
                if (iceSucceeded) {
                    LocalCandidate localCandidate = stream.getComponent(1).getSelectedPair().getLocalCandidate();
                    TransportAddress transportAddress = localCandidate.getTransportAddress();
                    return new IceNegotiationResult(
                        true,
                        transportAddress.getAddress().getHostAddress(),
                        transportAddress.getPort(),
                        agent,
                        stream
                    );
                } else {
                    logger.warn("ICE negotiation failed or timed out");
                    return new IceNegotiationResult(false, null, 0, agent, null);
                }
            } catch (Exception e) {
                logger.error("Error during ICE negotiation", e);
                return new IceNegotiationResult(false, null, 0, null, null);
            }
        });
    }
    
    /**
     * Represents the result of an ICE negotiation
     */
    public static class IceNegotiationResult {
        private final boolean success;
        private final String address;
        private final int port;
        private final Agent agent;
        private final IceMediaStream stream;
        
        public IceNegotiationResult(boolean success, String address, int port, 
                                    Agent agent, IceMediaStream stream) {
            this.success = success;
            this.address = address;
            this.port = port;
            this.agent = agent;
            this.stream = stream;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getAddress() {
            return address;
        }
        
        public int getPort() {
            return port;
        }
        
        public Agent getAgent() {
            return agent;
        }
        
        public IceMediaStream getStream() {
            return stream;
        }
    }
    
    /**
     * Represents the method used to discover the connection details
     */
    public enum DiscoveryMethod {
        UPNP,       // Using UPnP for port mapping and IP discovery
        STUN,       // Using STUN for NAT traversal
        LOCAL_ONLY  // Only local network, no external connectivity
    }
    
    /**
     * Represents connection details including IP and port information
     */
    public static class ConnectionDetails {
        private String localIp;
        private String externalIp;
        private int externalPort;
        private boolean portMapped;
        private DiscoveryMethod discoveryMethod;
        
        public String getLocalIp() {
            return localIp;
        }
        
        public void setLocalIp(String localIp) {
            this.localIp = localIp;
        }
        
        public String getExternalIp() {
            return externalIp;
        }
        
        public void setExternalIp(String externalIp) {
            this.externalIp = externalIp;
        }
        
        public int getExternalPort() {
            return externalPort;
        }
        
        public void setExternalPort(int externalPort) {
            this.externalPort = externalPort;
        }
        
        public boolean isPortMapped() {
            return portMapped;
        }
        
        public void setPortMapped(boolean portMapped) {
            this.portMapped = portMapped;
        }
        
        public DiscoveryMethod getDiscoveryMethod() {
            return discoveryMethod;
        }
        
        public void setDiscoveryMethod(DiscoveryMethod discoveryMethod) {
            this.discoveryMethod = discoveryMethod;
        }
        
        @Override
        public String toString() {
            return "ConnectionDetails{" +
                   "localIp='" + localIp + '\'' +
                   ", externalIp='" + externalIp + '\'' +
                   ", externalPort=" + externalPort +
                   ", portMapped=" + portMapped +
                   ", discoveryMethod=" + discoveryMethod +
                   '}';
        }
    }

    static {
        // Set ICE port range globally for all ICE agents
        System.setProperty("org.ice4j.ice.MIN_PORT", "5000");
        System.setProperty("org.ice4j.ice.MAX_PORT", "5100");
        System.setProperty("org.ice4j.ice.harvest.PREFERRED_PORT", "5000");
        // Debug: Print all ICE-related system properties
        System.getProperties().entrySet().stream()
            .filter(e -> e.getKey().toString().toLowerCase().contains("ice"))
            .forEach(e -> System.out.println("[ICE DEBUG] " + e.getKey() + " = " + e.getValue()));
    }

    // Utility method for safe port parsing
    private static int parsePort(String value, int defaultValue) {
        try {
            if (value == null || value.isEmpty()) return defaultValue;
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Shuffles an array in-place using the Fisher-Yates algorithm
     */
    private static void shuffleArray(String[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            // Simple swap
            String temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }
    
    /**
     * More robust method to get external IP using multiple STUN servers and optimized retry logic
     * 
     * @param maxAttempts Maximum number of STUN servers to try
     * @param timeoutMs Timeout in milliseconds for each attempt
     * @return The external IP address, or empty if unable to determine
     */
    public static Optional<String> getRobustExternalIpViaStun(int maxAttempts, int timeoutMs) {
        // Shuffle all available STUN servers for better distribution
        String[] shuffledServers = getShuffledStunServers();
        
        // First, pre-filter servers by testing connectivity
        String workingServer = NetworkUtils.findWorkingStunServer(shuffledServers, timeoutMs);
        if (workingServer != null) {
            try {
                // Try the working server first
                Optional<String> result = getExternalIpViaStun(workingServer);
                if (result.isPresent()) {
                    logger.info("Successfully discovered public IP {} via pre-tested STUN server {}", 
                                result.get(), workingServer);
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Pre-tested STUN server {} failed: {}", workingServer, e.getMessage());
            }
        }
        
        // If pre-filtering didn't work, try up to maxAttempts servers
        int attemptsToMake = Math.min(maxAttempts, shuffledServers.length);
        
        for (int i = 0; i < attemptsToMake; i++) {
            try {
                Optional<String> result = getExternalIpViaStun(shuffledServers[i]);
                if (result.isPresent()) {
                    logger.info("Successfully discovered public IP {} via STUN server {} (attempt {}/{})", 
                                result.get(), shuffledServers[i], i+1, attemptsToMake);
                    return result;
                }
            } catch (Exception e) {
                logger.warn("STUN server {} failed (attempt {}/{}): {}", 
                            shuffledServers[i], i+1, attemptsToMake, e.getMessage());
            }
        }
        
        // If all attempts failed, try the default method as a last resort
        try {
            return getExternalIpViaStun();
        } catch (Exception e) {
            logger.warn("All STUN servers failed to discover public IP");
            return Optional.empty();
        }
    }
    
    /**
     * Tries to establish a connection with multiple fallback strategies.
     * This method systematically attempts various techniques to establish a reliable connection,
     * falling back to the next approach if one fails.
     *
     * @param targetIp Initial target IP to try connecting to
     * @param targetPort The port to connect to
     * @param timeoutMs Connection timeout in milliseconds
     * @return The IP address that successfully connected, or empty if all attempts failed
     */
    public static CompletableFuture<Optional<String>> tryConnectionWithFallbacks(
            String targetIp, int targetPort, int timeoutMs) {
        
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        
        // Strategy 1: Try direct connection to the specified IP
        if (NetworkUtils.isValidIpAddress(targetIp)) {
            logger.info("Attempting direct connection to {}:{}", targetIp, targetPort);
            
            if (testDirectConnection(targetIp, targetPort, timeoutMs)) {
                logger.info("Direct connection to {}:{} successful", targetIp, targetPort);
                result.complete(Optional.of(targetIp));
                return result;
            }
            
            logger.info("Direct connection to {}:{} failed, trying alternatives", targetIp, targetPort);
        }
        
        // Strategy 2: If target IP is public, try finding other public IPs via STUN
        if (NetworkUtils.isPublicIpAddress(targetIp)) {
            Optional<String> externalIp = getRobustExternalIpViaStun(3, timeoutMs);
            if (externalIp.isPresent() && !externalIp.get().equals(targetIp)) {
                String newIp = externalIp.get();
                logger.info("Trying alternative public IP {}:{}", newIp, targetPort);
                
                if (testDirectConnection(newIp, targetPort, timeoutMs)) {
                    logger.info("Connection with alternative public IP {}:{} successful", newIp, targetPort);
                    result.complete(Optional.of(newIp));
                    return result;
                }
            }
            
            // If public IP approach failed, move to strategy 3
            tryLocalNetworkIps(targetIp, targetPort, timeoutMs, result);
        } else {
            // If target is not public, go straight to local network strategy
            tryLocalNetworkIps(targetIp, targetPort, timeoutMs, result);
        }
        
        return result;
    }
    
    /**
     * Helper method to try connecting with local network IPs
     */
    private static void tryLocalNetworkIps(
            String originalTargetIp, int targetPort, int timeoutMs, CompletableFuture<Optional<String>> result) {
        
        // Strategy 3: Try all local network IPs
        List<String> localIps = NetworkUtils.getAllLocalIpv4Addresses();
        
        // Add original target IP if it's not already in the list
        if (NetworkUtils.isValidIpAddress(originalTargetIp) && !localIps.contains(originalTargetIp)) {
            localIps.add(0, originalTargetIp); // Add at the beginning to try it first
        }
        
        // Prioritize IPs for more effective connection attempts
        localIps = NetworkUtils.prioritizeIpAddresses(localIps);
        
        logger.info("Trying local network IPs: {}", localIps);
        
        // Test all IPs in parallel for better efficiency
        NetworkUtils.testNetworkAddressesInParallel(localIps, targetPort, timeoutMs)
            .thenAccept(successfulIp -> {
                if (successfulIp.isPresent()) {
                    logger.info("Connection with local IP {}:{} successful", successfulIp.get(), targetPort);
                    result.complete(successfulIp);
                } else {
                    logger.warn("All connection attempts failed");
                    result.complete(Optional.empty());
                }
            });
    }
}
