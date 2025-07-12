package com.securetransfer.service.impl;

import com.securetransfer.service.P2PConnectionService;
import com.securetransfer.util.P2PConnectionManager;
import com.securetransfer.util.P2PConnectionManager.ConnectionDetails;
import com.securetransfer.util.UPnPManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the P2P connection service.
 */
@Service
public class P2PConnectionServiceImpl implements P2PConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(P2PConnectionServiceImpl.class);
    
    @Value("${p2p.enabled:true}")
    private boolean p2pEnabled;
    
    @Value("${p2p.upnp.enabled:true}")
    private boolean upnpEnabled;
    
    @Value("${p2p.stun.enabled:true}")
    private boolean stunEnabled;
    
    @Value("${p2p.connection.timeout:15000}")
    private int connectionTimeout;
    
    @Value("${p2p.listen.port:8444}")
    private int listenPort;

    @Value("${p2p.listen.port.range:15}")
    private int portRange;
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    private ConnectionDetails connectionDetails;
    
    @PostConstruct
    @Override
    public void initialize() {
        if (!p2pEnabled) {
            logger.info("P2P connections are disabled");
            return;
        }
        
        logger.info("Initializing P2P connection service");
        
        // Start listening for connections
        startServer();
        
        // Discover connection details
        getConnectionDetails().thenAccept(details -> {
            logger.info("Connection details discovered: {}", details);
            connectionDetails = details;
        });
    }
    
    @Override
    public CompletableFuture<ConnectionDetails> getConnectionDetails() {
        if (!p2pEnabled) {
            return CompletableFuture.completedFuture(new ConnectionDetails());
        }
        
        return P2PConnectionManager.discoverConnectionDetails(listenPort);
    }
    
    @Override
    public int startServer() {
        if (!p2pEnabled || serverRunning.get()) {
            return -1;
        }
        int port = -1;
        int maxPort = listenPort + portRange;
        for (int tryPort = listenPort; tryPort <= maxPort; tryPort++) {
            try {
                serverSocket = new ServerSocket(tryPort);
                port = tryPort;
                break;
            } catch (IOException ignored) {}
        }
        if (port == -1) {
            logger.error("All P2P ports in range {}–{} are in use. Please free a port and restart the app.", listenPort, maxPort);
            return -1;
        }
        listenPort = port;
        serverRunning.set(true);
        executorService.submit(this::acceptConnections);
        logger.info("P2P server started on port {}", listenPort);
        return listenPort;
    }
    
    private void acceptConnections() {
        logger.info("Listening for incoming P2P connections");
        
        while (serverRunning.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from {}", 
                          clientSocket.getInetAddress().getHostAddress());
                
                // Handle the client connection in a new thread
                executorService.submit(() -> handleClientConnection(clientSocket));
            } catch (IOException e) {
                if (serverRunning.get()) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }
    
    private void handleClientConnection(Socket clientSocket) {
        // TODO: Implement the protocol for file transfer over the socket
        try {
            // For now, just log the connection and close it
            logger.info("Handling connection from {}", 
                      clientSocket.getInetAddress().getHostAddress());
            
            // Keep the socket open for actual implementation
            // ...
            
            // clientSocket.close();
        } catch (Exception e) {
            logger.error("Error handling client connection", e);
        }
    }
    
    @Override
    public void stopServer() {
        if (!serverRunning.get()) {
            return;
        }
        
        serverRunning.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            logger.info("P2P server stopped");
        } catch (IOException e) {
            logger.error("Error stopping P2P server", e);
        }
    }
    
    @Override
    public CompletableFuture<Boolean> connectToPeer(String ip, int port) {
        if (!p2pEnabled) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Attempting to connect to peer at {}:{}", ip, port);
            return P2PConnectionManager.testDirectConnection(ip, port, connectionTimeout);
        }, executorService);
    }
    
    @Override
    public boolean isServerRunning() {
        return serverRunning.get();
    }
    
    @PreDestroy
    @Override
    public void shutdown() {
        stopServer();
        
        // Cleanup UPnP port mappings if needed
        if (p2pEnabled && upnpEnabled && connectionDetails != null && 
            connectionDetails.isPortMapped()) {
            UPnPManager.removePortMapping(connectionDetails.getExternalPort(), "TCP")
                .thenAccept(result -> {
                    if (result) {
                        logger.info("Successfully removed UPnP port mapping");
                    } else {
                        logger.warn("Failed to remove UPnP port mapping");
                    }
                });
        }
        
        // Also remove the WebSocket server port mapping if it exists
        UPnPManager.removePortMapping(8081, "TCP")
            .thenAccept(result -> {
                if (result) {
                    logger.info("Successfully removed WebSocket server UPnP port mapping");
                } else {
                    logger.warn("Failed to remove WebSocket server UPnP port mapping");
                }
            });
        
        executorService.shutdown();
        UPnPManager.shutdown();
        logger.info("P2P connection service shutdown complete");
    }
}
