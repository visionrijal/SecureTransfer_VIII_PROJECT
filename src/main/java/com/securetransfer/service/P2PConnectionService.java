package com.securetransfer.service;

import com.securetransfer.util.P2PConnectionManager.ConnectionDetails;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing peer-to-peer connections.
 */
public interface P2PConnectionService {
    
    /**
     * Initialize the P2P connection service
     */
    void initialize();
    
    /**
     * Get connection details for this peer
     * @return CompletableFuture with connection details
     */
    CompletableFuture<ConnectionDetails> getConnectionDetails();
    
    /**
     * Start a server to listen for incoming connections
     * @return The port the server is listening on
     */
    int startServer();
    
    /**
     * Stop the server
     */
    void stopServer();
    
    /**
     * Connect to a remote peer
     * @param ip The IP address of the remote peer
     * @param port The port of the remote peer
     * @return True if connection successful, false otherwise
     */
    CompletableFuture<Boolean> connectToPeer(String ip, int port);
    
    /**
     * Check if the service has an active server
     * @return True if the server is running
     */
    boolean isServerRunning();
    
    /**
     * Shutdown the service
     */
    void shutdown();
}
