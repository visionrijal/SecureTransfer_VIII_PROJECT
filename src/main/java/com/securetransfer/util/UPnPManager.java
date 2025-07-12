package com.securetransfer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility for UPnP port mapping and network discovery.
 * This class provides functionality to detect UPnP-enabled routers and
 * automatically set up port forwarding.
 */
public class UPnPManager {
    private static final Logger logger = LoggerFactory.getLogger(UPnPManager.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * Attempts to map a port on the router using UPnP
     * 
     * @param externalPort The external port to be exposed on the router
     * @param internalPort The internal port on this machine to forward to
     * @param protocol The protocol (TCP/UDP)
     * @param description Description for the port mapping
     * @return CompletableFuture that resolves to true if port mapping was successful
     */
    public static CompletableFuture<Boolean> mapPort(int externalPort, int internalPort, 
                                                    String protocol, String description) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Using Weupnp library for UPnP port mapping
                org.bitlet.weupnp.GatewayDiscover discover = new org.bitlet.weupnp.GatewayDiscover();
                discover.discover();
                
                org.bitlet.weupnp.GatewayDevice device = discover.getValidGateway();
                if (device == null) {
                    logger.warn("No UPnP gateway device found");
                    return false;
                }
                
                logger.info("Found gateway device: {}", device.getModelName());
                
                InetAddress localAddress = device.getLocalAddress();
                String externalIP = device.getExternalIPAddress();
                logger.info("External IP address: {}", externalIP);
                
                // Add port mapping
                boolean result = device.addPortMapping(
                    externalPort, internalPort, localAddress.getHostAddress(), 
                    protocol, description
                );
                
                if (result) {
                    logger.info("Port mapping added successfully: {}:{} -> {}:{}", 
                            externalIP, externalPort, localAddress.getHostAddress(), internalPort);
                } else {
                    logger.warn("Failed to add port mapping");
                }
                
                return result;
            } catch (Exception e) {
                logger.error("Error setting up UPnP port mapping", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Removes a previously mapped port
     * 
     * @param externalPort The external port to remove
     * @param protocol The protocol (TCP/UDP)
     * @return CompletableFuture that resolves to true if port mapping was removed successfully
     */
    public static CompletableFuture<Boolean> removePortMapping(int externalPort, String protocol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                org.bitlet.weupnp.GatewayDiscover discover = new org.bitlet.weupnp.GatewayDiscover();
                discover.discover();
                
                org.bitlet.weupnp.GatewayDevice device = discover.getValidGateway();
                if (device == null) {
                    logger.warn("No UPnP gateway device found");
                    return false;
                }
                
                boolean result = device.deletePortMapping(externalPort, protocol);
                
                if (result) {
                    logger.info("Port mapping removed successfully: {}", externalPort);
                } else {
                    logger.warn("Failed to remove port mapping");
                }
                
                return result;
            } catch (Exception e) {
                logger.error("Error removing UPnP port mapping", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Gets all local IP addresses
     * @return An array of local IP addresses
     */
    public static Optional<String> getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isLoopback() && ni.isUp()) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        // Filter for IPv4 addresses that are not loopback
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            return Optional.of(addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("Error getting local IP address", e);
        }
        return Optional.empty();
    }
    
    /**
     * Attempts to get the external IP address using UPnP
     * @return Optional containing the external IP if available
     */
    public static Optional<String> getExternalIpAddress() {
        try {
            org.bitlet.weupnp.GatewayDiscover discover = new org.bitlet.weupnp.GatewayDiscover();
            discover.discover();
            
            org.bitlet.weupnp.GatewayDevice device = discover.getValidGateway();
            if (device == null) {
                logger.warn("No UPnP gateway device found");
                return Optional.empty();
            }
            
            String externalIP = device.getExternalIPAddress();
            return Optional.of(externalIP);
        } catch (Exception e) {
            logger.error("Error getting external IP address", e);
            return Optional.empty();
        }
    }
    
    /**
     * Checks if UPnP is available on the network
     * @return true if UPnP is available
     */
    public static boolean isUPnPAvailable() {
        try {
            org.bitlet.weupnp.GatewayDiscover discover = new org.bitlet.weupnp.GatewayDiscover();
            discover.discover();
            return discover.getValidGateway() != null;
        } catch (Exception e) {
            logger.error("Error checking UPnP availability", e);
            return false;
        }
    }
    
    /**
     * Shutdown the executor service
     */
    public static void shutdown() {
        executorService.shutdown();
    }
}
