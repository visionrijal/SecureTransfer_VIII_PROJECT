package com.securetransfer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility class for network-related operations such as IP address discovery.
 */
public class NetworkUtils {
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * Gets the local (non-loopback, non-link-local) IP address of the machine.
     * This method prioritizes IPv4 addresses and tries to find the most
     * appropriate IP for external connections.
     *
     * @return An Optional containing the local IP address, or empty if none could be found
     */
    public static Optional<String> getLocalIpAddress() {
        try {
            // First try: Look for a non-loopback, non-link-local IPv4 address
            Optional<String> ipv4 = findSuitableIpv4Address();
            if (ipv4.isPresent()) {
                return ipv4;
            }

            // Second try: Look for any non-loopback IPv6 address
            Optional<String> ipv6 = findSuitableIpv6Address();
            if (ipv6.isPresent()) {
                return ipv6;
            }

            // Last resort: Use local loopback
            return Optional.of(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            logger.error("Failed to determine local IP address: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Gets all local IPv4 addresses (internal implementation)
     * @return List of IPv4 addresses
     */
    public static List<String> getAllLocalIpv4AddressesInternal() {
        List<String> ipv4Addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address) {
                            ipv4Addresses.add(address.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error gathering all IPv4 addresses: {}", e.getMessage(), e);
        }
        return ipv4Addresses;
    }

    /**
     * Finds a suitable IPv4 address from physical interfaces
     * 
     * @return Optional with the best IPv4 address or empty if none found
     */
    private static Optional<String> findSuitableIpv4Address() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            // Prioritize ethernet and wifi interfaces
            List<String> priorityInterfacePatterns = Arrays.asList(
                "eth", "en", "wlan", "wifi", "wi-fi", "wireless", "net", "eno", "enp", "ens", "wlp"
            );
            
            // First pass: Try to find an address from a priority interface
            for (String interfacePattern : priorityInterfacePatterns) {
                Optional<String> result = findAddressFromInterfacePattern(networkInterfaces, interfacePattern, true);
                if (result.isPresent()) {
                    return result;
                }
                // Reset the enumeration for the next pattern
                networkInterfaces = NetworkInterface.getNetworkInterfaces();
            }
            
            // Second pass: Accept any non-loopback, non-link-local IPv4
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                            return Optional.of(address.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding suitable IPv4 address: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Finds a suitable IPv6 address
     * 
     * @return Optional with the best IPv6 address or empty if none found
     */
    private static Optional<String> findSuitableIpv6Address() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet6Address && !address.isLinkLocalAddress()) {
                            // Format IPv6 with brackets for URL format
                            String ipv6 = address.getHostAddress();
                            if (ipv6.contains("%")) {
                                // Remove scope id
                                ipv6 = ipv6.substring(0, ipv6.indexOf('%'));
                            }
                            return Optional.of(ipv6);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding suitable IPv6 address: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Finds an address from interfaces matching a specific pattern
     * 
     * @param networkInterfaces Enumeration of network interfaces
     * @param pattern Pattern to match in interface name
     * @param ipv4Only Whether to return only IPv4 addresses
     * @return Optional with the matching address or empty if none found
     */
    private static Optional<String> findAddressFromInterfacePattern(
            Enumeration<NetworkInterface> networkInterfaces, 
            String pattern, 
            boolean ipv4Only) {
        try {
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback() 
                        && networkInterface.getName().toLowerCase().contains(pattern.toLowerCase())) {
                    
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if ((ipv4Only && address instanceof Inet4Address || !ipv4Only) 
                                && !address.isLinkLocalAddress()) {
                            return Optional.of(address.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding address from interface pattern {}: {}", pattern, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Checks if a network interface is likely to be a physical interface
     * (not virtual, not loopback, and up)
     *
     * @param networkInterface The network interface to check
     * @return true if the interface appears to be physical
     */
    public static boolean isPhysicalInterface(NetworkInterface networkInterface) throws SocketException {
        if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
            return false;
        }
        
        // Check if it has a hardware address (MAC)
        byte[] hardwareAddress = networkInterface.getHardwareAddress();
        if (hardwareAddress == null || hardwareAddress.length == 0) {
            return false;
        }
        
        // Avoid common virtual interface names
        String name = networkInterface.getName().toLowerCase();
        return !name.contains("vmnet") && !name.contains("virtual") && 
               !name.contains("docker") && !name.contains("veth") &&
               !name.contains("virbr") && !name.contains("vnet");
    }

    /**
     * Gets all non-loopback IPv4 addresses of this machine.
     * This method is more robust than the simpler approaches as it handles
     * multiple network interfaces and filters appropriately.
     *
     * @return List of local IP addresses
     */
    public static List<String> getAllLocalIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // Skip loopback, virtual, non-running interfaces
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    // Only include IPv4 addresses that aren't loopback
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
            
            // Sort addresses to prioritize non-link-local addresses
            if (!addresses.isEmpty()) {
                addresses.sort((a, b) -> {
                    // Prioritize addresses that are not link-local (169.254.x.x) or docker (172.17.x.x)
                    boolean aIsLinkLocal = a.startsWith("169.254.") || a.startsWith("172.17.");
                    boolean bIsLinkLocal = b.startsWith("169.254.") || b.startsWith("172.17.");
                    return Boolean.compare(aIsLinkLocal, bIsLinkLocal);
                });
            }
        } catch (SocketException e) {
            logger.error("Error retrieving network interfaces: {}", e.getMessage());
        }
        return addresses;
    }

    /**
     * Checks if an IP address is valid
     * 
     * @param ip The IP address string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            
            // Additional check: try to create an InetAddress
            InetAddress.getByName(ip);
            return true;
        } catch (NumberFormatException | UnknownHostException e) {
            return false;
        }
    }

    /**
     * Checks if an IP address is a local/private network address
     * 
     * @param ip The IP address to check
     * @return true if local/private, false otherwise
     */
    public static boolean isLocalNetworkAddress(String ip) {
        if (!isValidIpAddress(ip)) {
            return false;
        }
        
        // Check for localhost
        if (ip.equals("127.0.0.1") || ip.equals("::1")) {
            return true;
        }
        
    // Check for private network ranges
    return ip.startsWith("10.") || 
           ip.startsWith("192.168.") || 
           (ip.startsWith("172.") && 
            (Integer.parseInt(ip.split("\\.")[1]) >= 16 && 
             Integer.parseInt(ip.split("\\.")[1]) <= 31)) || 
           ip.startsWith("169.254."); // Link-local
    }

    /**
     * Test if a specific port is open on a host
     * 
     * @param host The host to test
     * @param port The port to test
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if port is open, false otherwise
     */
    public static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A more robust version of isPortOpen that attempts multiple connection strategies
     * 
     * @param host The host to connect to
     * @param port The port to connect to
     * @param timeoutMs Connection timeout in milliseconds
     * @param retries Number of retry attempts
     * @return true if any connection attempt succeeds, false otherwise
     */
    public static boolean isPortOpenRobust(String host, int port, int timeoutMs, int retries) {
        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                if (isPortOpen(host, port, timeoutMs)) {
                    return true;
                }
                
                // If first attempt fails, try with a longer timeout for subsequent attempts
                timeoutMs = Math.min(timeoutMs * 2, 10000); // Increase timeout, max 10 seconds
                
                // Short delay between attempts
                Thread.sleep(500);
            } catch (Exception e) {
                logger.debug("Attempt {} failed for {}:{} - {}", attempt+1, host, port, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Gets all IP addresses of the local machine, sorted by reliability/usability
     * 
     * @return List of IP addresses, sorted with most usable first
     */
    public static List<String> getAllLocalIpAddressesSorted() {
        List<String> allAddresses = new ArrayList<>();
        
        // Add IPv4 addresses first (preferred)
        List<String> ipv4Addresses = getAllLocalIpv4Addresses();
        if (!ipv4Addresses.isEmpty()) {
            // Sort by reliability - avoid link-local addresses
            ipv4Addresses.sort((a, b) -> {
                // Prefer non-link-local, non-docker addresses
                boolean aIsSpecial = a.startsWith("169.254.") || a.startsWith("172.17.");
                boolean bIsSpecial = b.startsWith("169.254.") || b.startsWith("172.17.");
                
                if (aIsSpecial && !bIsSpecial) return 1;  // b is better
                if (!aIsSpecial && bIsSpecial) return -1; // a is better
                return 0;  // equal preference
            });
            allAddresses.addAll(ipv4Addresses);
        }
        
        // Add IPv6 addresses next (if any IPv4 addresses weren't found)
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet6Address && !addr.isLinkLocalAddress()) {
                            String ipv6 = addr.getHostAddress();
                            if (ipv6.contains("%")) {
                                ipv6 = ipv6.substring(0, ipv6.indexOf("%")); // Remove scope ID
                            }
                            if (!allAddresses.contains(ipv6)) {
                                allAddresses.add(ipv6);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error gathering IPv6 addresses: {}", e.getMessage());
        }
        
        // Add loopback as last resort
        if (!allAddresses.contains("127.0.0.1")) {
            allAddresses.add("127.0.0.1");
        }
        
        return allAddresses;
    }

    /**
     * Gets the best local IP address for external connections with enhanced reliability.
     * This method improves on getLocalIpAddress by prioritizing interfaces and
     * attempting multiple discovery strategies.
     *
     * @return An Optional containing the best local IP address for external connectivity
     */
    public static Optional<String> getBestLocalIpAddress() {
        try {
            // First priority: Non-loopback, non-link-local IPv4 from physical interfaces
            Optional<String> physicalIpv4 = findSuitableIpv4Address();
            if (physicalIpv4.isPresent()) {
                logger.info("Using physical interface IPv4: {}", physicalIpv4.get());
                return physicalIpv4;
            }
            
            // Second priority: Any usable IPv4 address
            List<String> allIpv4Addresses = getAllLocalIpv4Addresses();
            if (!allIpv4Addresses.isEmpty()) {
                // Sort by priority - avoid link-local and special-use addresses
                String bestIpv4 = allIpv4Addresses.stream()
                    .filter(ip -> !ip.startsWith("169.254."))  // Avoid link-local
                    .filter(ip -> !ip.startsWith("172.17."))   // Common Docker subnet
                    .findFirst()
                    .orElse(allIpv4Addresses.get(0));
                
                logger.info("Using best available IPv4: {}", bestIpv4);
                return Optional.of(bestIpv4);
            }
            
            // Third priority: Non-link-local IPv6
            Optional<String> ipv6 = findSuitableIpv6Address();
            if (ipv6.isPresent()) {
                logger.info("Using IPv6: {}", ipv6.get());
                return ipv6;
            }
            
            // Last resort: Use local loopback
            String loopback = InetAddress.getLocalHost().getHostAddress();
            logger.info("Falling back to loopback: {}", loopback);
            return Optional.of(loopback);
        } catch (Exception e) {
            logger.error("Failed to determine best local IP address: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Tests if a host is reachable via the specified port within a timeout
     *
     * @param host The host to test
     * @param port The port to test
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if the host is reachable, false otherwise
     */
    public static boolean isHostReachable(String host, int timeoutMs) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isReachable(timeoutMs);
        } catch (Exception e) {
            logger.debug("Host {} is not reachable: {}", host, e.getMessage());
            return false;
        }
    }
    

    /**
     * Checks if an IP address is a public (non-private, non-loopback) address
     * 
     * @param ip The IP address to check
     * @return true if public, false otherwise
     */
    public static boolean isPublicIpAddress(String ip) {
        if (!isValidIpAddress(ip)) {
            return false;
        }
        
        // Check if it's NOT a local network address or loopback
        return !isLocalNetworkAddress(ip) && !ip.equals("127.0.0.1");
    }

    /**
     * Test connectivity to multiple STUN servers and returns the first one that responds
     * 
     * @param stunServers Array of STUN servers to test
     * @param timeoutMs Timeout in milliseconds for each test
     * @return The first responsive STUN server, or null if all fail
     */
    public static String findWorkingStunServer(String[] stunServers, int timeoutMs) {
        if (stunServers == null || stunServers.length == 0) {
            return null;
        }
        
        for (String stunServer : stunServers) {
            try {
                String[] parts = stunServer.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3478;
                
                if (isPortOpen(host, port, timeoutMs)) {
                    logger.info("Found working STUN server: {}", stunServer);
                    return stunServer;
                }
            } catch (Exception e) {
                logger.warn("Error testing STUN server {}: {}", stunServer, e.getMessage());
            }
        }
        
        return null;
    }

    /**
     * Tests connectivity to multiple ports on a host in parallel
     * 
     * @param host The host to test
     * @param ports Array of ports to test
     * @param timeoutMs Timeout in milliseconds for each test
     * @return Array of open ports
     */
    public static List<Integer> findOpenPorts(String host, int[] ports, int timeoutMs) {
        List<Integer> openPorts = new ArrayList<>();
        
        for (int port : ports) {
            if (isPortOpen(host, port, timeoutMs)) {
                openPorts.add(port);
            }
        }
        
        return openPorts;
    }

    /**
     * Attempts to find a working IP and port combination from a list of options
     * 
     * @param ipAddresses List of IP addresses to try
     * @param port Port to test
     * @param timeoutMs Timeout in milliseconds for each test
     * @return Optional with the first working IP, or empty if none work
     */
    public static Optional<String> findWorkingIpAndPort(List<String> ipAddresses, int port, int timeoutMs) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return Optional.empty();
        }
        
        for (String ip : ipAddresses) {
            if (isPortOpen(ip, port, timeoutMs)) {
                logger.info("Found working connection at {}:{}", ip, port);
                return Optional.of(ip);
            }
        }
        
        return Optional.empty();
    }

    /**
     * Filters a list of IP addresses to include only those that are likely valid for external connections
     * 
     * @param ipAddresses List of IP addresses to filter
     * @return Filtered list of valid external IP addresses
     */
    public static List<String> filterValidExternalIps(List<String> ipAddresses) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return new ArrayList<>();
        }
        
        return ipAddresses.stream()
                .filter(NetworkUtils::isValidIpAddress)
                .filter(ip -> !ip.equals("127.0.0.1"))          // Not localhost
                .filter(ip -> !ip.startsWith("169.254."))       // Not link-local
                .filter(ip -> !ip.startsWith("0."))             // Not special-use
                .collect(Collectors.toList());
    }

    /**
     * Prioritizes a list of IP addresses for connection attempts
     * 
     * @param ipAddresses List of IP addresses to prioritize
     * @return Prioritized list of IP addresses
     */
    public static List<String> prioritizeIpAddresses(List<String> ipAddresses) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> prioritized = new ArrayList<>();
        
        // First priority: Public IPs
        ipAddresses.stream()
                .filter(NetworkUtils::isPublicIpAddress)
                .forEach(prioritized::add);
        
        // Second priority: Private network IPs (except link-local)
        ipAddresses.stream()
                .filter(ip -> isLocalNetworkAddress(ip) && !ip.startsWith("169.254."))
                .forEach(prioritized::add);
        
        // Last priority: Any remaining IPs
        ipAddresses.stream()
                .filter(ip -> !prioritized.contains(ip))
                .forEach(prioritized::add);
        
        return prioritized;
    }

    /**
     * Tests multiple network addresses in parallel to find the most reliable one
     * 
     * @param addresses List of IP addresses to test
     * @param port Port to test on each address
     * @param timeoutMs Timeout in milliseconds for each test
     * @return The first address that successfully connects, or empty if all fail
     */
    public static CompletableFuture<Optional<String>> testNetworkAddressesInParallel(
            List<String> addresses, int port, int timeoutMs) {
        
        if (addresses == null || addresses.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        
        // Track how many addresses have been tested
        final int[] completed = new int[1];
        completed[0] = 0;
        
        // For each address, test connection in a separate thread
        for (String address : addresses) {
            CompletableFuture.runAsync(() -> {
                try {
                    if (isPortOpen(address, port, timeoutMs)) {
                        // Only complete if not already completed
                        if (!result.isDone()) {
                            result.complete(Optional.of(address));
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error testing address {}: {}", address, e.getMessage());
                } finally {
                    // Increment completed count
                    synchronized (completed) {
                        completed[0]++;
                        // If all addresses have been tested and none succeeded, complete with empty
                        if (completed[0] >= addresses.size() && !result.isDone()) {
                            result.complete(Optional.empty());
                        }
                    }
                }
            });
        }
        
        // Set a timeout in case threads hang
        CompletableFuture.delayedExecutor(timeoutMs + 500, TimeUnit.MILLISECONDS)
            .execute(() -> {
                if (!result.isDone()) {
                    result.complete(Optional.empty());
                }
            });
        
        return result;
    }

    /**
     * Validates multiple connection parameters (IP, port, protocol) to find the best working combination
     * 
     * @param ips List of IP addresses to test
     * @param ports List of ports to test
     * @param timeoutMs Timeout in milliseconds for each test
     * @return Map of successful IP to port combinations
     */
    public static CompletableFuture<Map<String, List<Integer>>> validateConnectionParameters(
            List<String> ips, List<Integer> ports, int timeoutMs) {
        
        Map<String, List<Integer>> results = new java.util.concurrent.ConcurrentHashMap<>();
        
        if (ips == null || ips.isEmpty() || ports == null || ports.isEmpty()) {
            return CompletableFuture.completedFuture(results);
        }
        
        CompletableFuture<Void> allTests = CompletableFuture.allOf(
            ips.stream().map(ip -> CompletableFuture.runAsync(() -> {
                List<Integer> openPorts = new ArrayList<>();
                
                for (Integer port : ports) {
                    try {
                        if (isPortOpen(ip, port, timeoutMs)) {
                            openPorts.add(port);
                        }
                    } catch (Exception e) {
                        logger.debug("Error testing {}:{}: {}", ip, port, e.getMessage());
                    }
                }
                
                if (!openPorts.isEmpty()) {
                    results.put(ip, openPorts);
                }
            })).toArray(CompletableFuture[]::new)
        );
        
        return allTests.thenApply(v -> results);
    }

    /**
     * Runs a comprehensive network connectivity diagnostic
     * This utility method helps diagnose NAT traversal and connection issues by running
     * a comprehensive test of various networking components
     * 
     * @return A report of the network diagnostic
     */
    public static String runNetworkDiagnostic() {
        StringBuilder report = new StringBuilder();
        report.append("==== Network Diagnostic Report ====\n");
        report.append("Timestamp: ").append(java.time.LocalDateTime.now()).append("\n\n");
        
        // Test 1: Local Network Interfaces
        report.append("-- Local Network Interfaces --\n");
        try {
            List<String> localIps = getAllLocalIpv4Addresses();
            report.append("IPv4 Addresses: ").append(localIps).append("\n");
            
            Optional<String> bestLocalIp = getBestLocalIpAddress();
            report.append("Best Local IP: ").append(bestLocalIp.orElse("None found")).append("\n\n");
        } catch (Exception e) {
            report.append("Error getting local interfaces: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test 2: STUN Server Connectivity
        report.append("-- STUN Server Connectivity --\n");
        String[] stunServers = P2PConnectionManager.getShuffledStunServers();
        if (stunServers.length > 0) {
            int successCount = 0;
            for (String stunServer : stunServers) {
                try {
                    String[] parts = stunServer.split(":");
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    boolean reachable = isPortOpen(host, port, 2000);
                    report.append(stunServer).append(": ").append(reachable ? "REACHABLE" : "UNREACHABLE").append("\n");
                    if (reachable) successCount++;
                } catch (Exception e) {
                    report.append(stunServer).append(": ERROR - ").append(e.getMessage()).append("\n");
                }
            }
            report.append("STUN Server Reachability: ").append(successCount).append("/").append(stunServers.length).append("\n\n");
        } else {
            report.append("No STUN servers configured\n\n");
        }
        
        // Test 3: Public IP Discovery
        report.append("-- Public IP Discovery --\n");
        try {
            Optional<String> publicIp = P2PConnectionManager.getRobustExternalIpViaStun(3, 3000);
            report.append("Public IP via STUN: ").append(publicIp.orElse("Failed to discover")).append("\n");
            
            if (publicIp.isPresent()) {
                report.append("IP Type: ").append(isPublicIpAddress(publicIp.get()) ? "Public" : "Private/Local").append("\n");
            }
            report.append("\n");
        } catch (Exception e) {
            report.append("Error discovering public IP: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test 4: UPnP Status
        report.append("-- UPnP Status --\n");
        try {
            boolean upnpAvailable = UPnPManager.isUPnPAvailable();
            report.append("UPnP Available: ").append(upnpAvailable).append("\n");
            
            if (upnpAvailable) {
                Optional<String> upnpExternalIp = UPnPManager.getExternalIpAddress();
                report.append("UPnP External IP: ").append(upnpExternalIp.orElse("Failed to discover")).append("\n");
            }
            report.append("\n");
        } catch (Exception e) {
            report.append("Error checking UPnP: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test 5: Internet Connectivity
        report.append("-- Internet Connectivity --\n");
        String[] testSites = {"google.com:80", "cloudflare.com:80", "github.com:443"};
        for (String site : testSites) {
            try {
                String[] parts = site.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                boolean reachable = isPortOpen(host, port, 3000);
                report.append(site).append(": ").append(reachable ? "REACHABLE" : "UNREACHABLE").append("\n");
            } catch (Exception e) {
                report.append(site).append(": ERROR - ").append(e.getMessage()).append("\n");
            }
        }
        report.append("\n");
        
        // Test 6: Local WebSocket Server
        report.append("-- Local WebSocket Server --\n");
        try {
            boolean wsServerRunning = isPortOpen("127.0.0.1", 8445, 1000);
            report.append("WebSocket Server (localhost:8445): ").append(wsServerRunning ? "RUNNING" : "NOT RUNNING").append("\n\n");
        } catch (Exception e) {
            report.append("Error checking WebSocket server: ").append(e.getMessage()).append("\n\n");
        }
        
        // Recommendations
        report.append("-- Recommendations --\n");
        // Add conditional recommendations based on test results
        
        report.append("\n==== End of Report ====");
        
        return report.toString();
    }
}
