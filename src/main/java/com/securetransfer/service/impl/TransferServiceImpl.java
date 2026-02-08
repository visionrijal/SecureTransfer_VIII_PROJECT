package com.securetransfer.service.impl;

import com.securetransfer.model.entity.ReceiverTransfer;
import com.securetransfer.model.entity.SenderTransfer;
import com.securetransfer.repository.ReceiverTransferRepository;
import com.securetransfer.repository.SenderTransferRepository;
import com.securetransfer.service.EncryptionService;
import com.securetransfer.service.TransferService;
import com.securetransfer.service.WebSocketService;
import com.securetransfer.service.WebSocketService.TransferSession;
import com.securetransfer.service.WebSocketService.SenderInfo;
import com.securetransfer.service.WebSocketService.ReceiverInfo;
import com.securetransfer.util.WebSocketClientManager;
import com.securetransfer.util.ToastNotification;
import com.securetransfer.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.Arrays;
import org.java_websocket.client.WebSocketClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import jakarta.annotation.PostConstruct;
import com.securetransfer.service.impl.SecureTransferWebSocketServer;
import org.java_websocket.WebSocket;
import java.util.function.BiConsumer;

/**
 * Implementation of transfer service for secure file transfer operations.
 * Handles file encryption, transfer coordination, and history tracking.
 */
@Service
@Transactional
public class TransferServiceImpl implements TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferServiceImpl.class);

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private EncryptionService encryptionService;

    private final WebSocketClientManager webSocketClientManager = new WebSocketClientManager();

    @Autowired
    private SenderTransferRepository senderTransferRepository;

    @Autowired
    private ReceiverTransferRepository receiverTransferRepository;

    @Autowired
    private SecureTransferWebSocketServer webSocketServer;

    // Active transfer sessions
    private final Map<String, TransferSession> activeSessions = new ConcurrentHashMap<>();

    // File data storage (temporary, in-memory)
    private final Map<String, byte[]> receivedFiles = new ConcurrentHashMap<>();

    // Transfer progress tracking
    private final Map<String, TransferProgress> transferProgress = new ConcurrentHashMap<>();

    // Store active WebSocket clients by transfer code
    private final Map<String, WebSocketClient> activeClients = new ConcurrentHashMap<>();

    private final Map<String, ByteArrayOutputStream> incomingFileBuffers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void registerWebSocketCallbacks() {
        webSocketServer.setReceiverConnectedCallback(this::onReceiverConnected);
    }

    private void onReceiverConnected(String transferCode, WebSocket conn) {
        logger.info("Receiver connected (callback) - showing confirmation dialog for transfer code: {}", transferCode);
        javafx.application.Platform.runLater(() -> {
            try {
                TransferSession session = activeSessions.get(transferCode);
                if (session != null) {
                    String fileName = session.getFileName();
                    long fileSize = session.getFileSize();
                    showTransferConfirmationDialog(transferCode, fileName, fileSize);
                } else {
                    logger.warn("No transfer session found for code: {}", transferCode);
                    ToastNotification.show(null, "Transfer session not found", ToastNotification.NotificationType.ERROR,
                            javafx.util.Duration.seconds(3), 70);
                }
            } catch (Exception e) {
                logger.error("Error showing confirmation dialog: {}", e.getMessage());
                ToastNotification.show(null, "Error showing confirmation: " + e.getMessage(),
                        ToastNotification.NotificationType.ERROR, javafx.util.Duration.seconds(3), 70);
            }
        });
    }

    @Override
    public CompletableFuture<TransferSession> initiateTransfer(String transferCode, List<File> files, String username) {
        logger.info("Initiating transfer for code: {}", transferCode);
        CompletableFuture<TransferSession> future = new CompletableFuture<>();

        // Create a unique session ID for this transfer
        String sessionId = UUID.randomUUID().toString();

        // Create sender info for registration
        SenderInfo senderInfo = new SenderInfo(
                UUID.randomUUID().toString(), // Generate device ID
                username, // Username passed from the UI
                sessionId // Unique session ID
        );

        // Calculate total file size
        final long totalFileSize = calculateTotalFileSize(files);

        // Get the first file name (or combined name if multiple)
        String displayFileName;
        if (files.size() == 1) {
            displayFileName = files.get(0).getName();
        } else {
            displayFileName = files.size() + " files";
        }

        // Register sender with the WebSocket service
        CompletableFuture<Void> regFuture = webSocketService.registerSender(transferCode, senderInfo, displayFileName,
                totalFileSize);
        logger.info("registerSender future returned for transfer code: {}", transferCode);
        regFuture
                .thenRun(() -> {
                    logger.info("Sender registered successfully for transfer code: {}", transferCode);

                    // Store sender connection details for receiver lookup
                    try {
                        // Get the sender's IP and WebSocket port
                        String senderIp = NetworkUtils.getLocalIpAddress().orElse("127.0.0.1");
                        int websocketPort = webSocketServer.getActualPort(); // Get actual WebSocket server port

                        // Store the connection details
                        storeSenderConnectionDetails(transferCode, senderIp, websocketPort);
                        logger.info("Stored sender connection details for transfer {}: {}:{}", transferCode, senderIp,
                                websocketPort);
                    } catch (Exception e) {
                        logger.warn("Failed to store sender connection details: {}", e.getMessage());
                    }

                    // Save transfer records for each file
                    for (File file : files) {
                        try {
                            // Create and save a sender transfer record
                            SenderTransfer transfer = new SenderTransfer();
                            transfer.setSessionId(sessionId);
                            transfer.setReceiverCode(transferCode);
                            transfer.setFileName(file.getName());
                            transfer.setFileSize(file.length());
                            transfer.setStartTime(LocalDateTime.now());
                            transfer.setReceiverUsername(username);
                            transfer.setTransferStatus(SenderTransfer.TransferStatus.PENDING);

                            // Generate checksum for verification
                            String checksum = calculateChecksum(file);
                            transfer.setChecksum(checksum);

                            // Save to repository
                            senderTransferRepository.save(transfer);

                        } catch (Exception e) {
                            logger.error("Error saving transfer record for file: {}", file.getName(), e);
                        }
                    }
                })
                .thenRun(() -> {
                    // Connect sender to its own WebSocket server to receive notifications
                    logger.info("=== SECOND THENRUN BLOCK EXECUTING ===");
                    logger.info("Connecting sender to WebSocket server for transfer code: {}", transferCode);
                    try {
                        int websocketPort = webSocketServer.getActualPort();
                        List<String> connectAddresses = new ArrayList<>();
                        connectAddresses.add("127.0.0.1:" + websocketPort);
                        // Add LAN IP
                        Optional<String> localIp = NetworkUtils.getLocalIpAddress();
                        localIp.ifPresent(ip -> connectAddresses.add(ip + ":" + websocketPort));
                        // Optionally add public IP if available (for NAT traversal, etc.)
                        // You can add logic here to fetch public IP if needed
                        logger.info("Attempting to connect to: {}", connectAddresses);

                        webSocketClientManager.connect(
                                transferCode,
                                "sender",
                                connectAddresses, // Connect to all possible addresses
                                msg -> logger.info("Sender connection status: {}", msg),
                                err -> logger.error("Sender connection error: {}", err),
                                url -> logger.info("Sender WebSocket open: {}", url),
                                reason -> logger.info("Sender WebSocket closed: {}", reason),
                                msg -> handleIncomingMessage(transferCode, msg),
                                bytes -> handleIncomingBinary(transferCode, bytes)).thenAccept(connResult -> {
                                    activeClients.put(transferCode, connResult.client);
                                    logger.info("Sender connected to WebSocket server for transfer code: {}",
                                            transferCode);

                                    // Create and store the transfer session
                                    TransferSession session = new TransferSession(
                                            transferCode,
                                            senderInfo,
                                            null, // receiver info not available yet
                                            displayFileName,
                                            totalFileSize);
                                    activeSessions.put(transferCode, session);

                                    // Register the callback for when receiver connects
                                    registerReceiverConnectionCallback(transferCode, () -> {
                                        logger.info("Receiver connection callback triggered for transfer code: {}",
                                                transferCode);
                                        TransferSession currentSession = activeSessions.get(transferCode);
                                        if (currentSession != null) {
                                            String fileName = currentSession.getFileName();
                                            long fileSize = currentSession.getFileSize();
                                            showTransferConfirmationDialog(transferCode, fileName, fileSize);
                                        } else {
                                            logger.warn("No transfer session found for code: {}", transferCode);
                                        }
                                    });

                                    future.complete(session);
                                }).exceptionally(ex -> {
                                    logger.error("Failed to connect sender to WebSocket server: {}", ex.getMessage(),
                                            ex);
                                    // Still complete the future with a session, but log the error
                                    TransferSession session = new TransferSession(
                                            transferCode,
                                            senderInfo,
                                            null,
                                            displayFileName,
                                            totalFileSize);
                                    activeSessions.put(transferCode, session);
                                    future.complete(session);
                                    return null;
                                });
                    } catch (Exception e) {
                        logger.error("Exception while connecting sender to WebSocket server: {}", e.getMessage(), e);
                        // Still complete the future with a session, but log the error
                        TransferSession session = new TransferSession(
                                transferCode,
                                senderInfo,
                                null,
                                displayFileName,
                                totalFileSize);
                        activeSessions.put(transferCode, session);
                        future.complete(session);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error registering sender: {}", ex.getMessage(), ex);
                    future.completeExceptionally(new RuntimeException("Failed to register sender: " + ex.getMessage()));
                    return null;
                });

        return future;
    }

    @Override
    public CompletableFuture<TransferSession> initiateTransfer(String transferCode, List<File> files, String username,
            String fileName, long fileSize) {
        logger.info("Initiating transfer for code: {} with explicit file info", transferCode);
        CompletableFuture<TransferSession> future = new CompletableFuture<>();

        // Create a unique session ID for this transfer
        String sessionId = UUID.randomUUID().toString();
        String deviceId = UUID.randomUUID().toString();

        // Create sender info for registration
        SenderInfo senderInfo = new SenderInfo(deviceId, username, sessionId);

        // Register sender with the WebSocket service
        CompletableFuture<Void> regFuture = webSocketService.registerSender(transferCode, senderInfo, fileName,
                fileSize);
        logger.info("registerSender future returned for transfer code: {}", transferCode);
        regFuture
                .thenRun(() -> {
                    logger.info("=== FIRST THENRUN BLOCK EXECUTING ===");
                    logger.info("Sender registered successfully for transfer code: {}", transferCode);

                    try {
                        // Store the sender's local IP for receiver connection
                        storeSenderLocalIp(transferCode);
                        logger.info("Stored sender local IP for transfer code: {}", transferCode);

                        // Create and save a sender transfer record for the file
                        SenderTransfer transfer = new SenderTransfer();
                        transfer.setSessionId(sessionId);
                        transfer.setReceiverCode(transferCode);
                        transfer.setFileName(fileName);
                        transfer.setFileSize(fileSize);
                        transfer.setStartTime(LocalDateTime.now());
                        transfer.setReceiverUsername(username);
                        transfer.setTransferStatus(SenderTransfer.TransferStatus.PENDING);

                        // Save to repository
                        senderTransferRepository.save(transfer);
                        logger.info("Saved sender transfer record for transfer code: {}", transferCode);
                        logger.info("=== FIRST THENRUN BLOCK COMPLETED SUCCESSFULLY ===");
                    } catch (Exception e) {
                        logger.error("Error in first thenRun block for transfer code {}: {}", transferCode,
                                e.getMessage(), e);
                        throw e; // Re-throw to prevent the next thenRun from executing
                    }
                })
                .thenRun(() -> {
                    // Connect sender to its own WebSocket server to receive notifications
                    logger.info("=== SECOND THENRUN BLOCK EXECUTING ===");
                    logger.info("Connecting sender to WebSocket server for transfer code: {}", transferCode);
                    try {
                        int websocketPort = webSocketServer.getActualPort();
                        logger.info("WebSocket server is running on port: {}", websocketPort);
                        List<String> localhostAddresses = Arrays.asList("127.0.0.1:" + websocketPort);
                        logger.info("Attempting to connect to: {}", localhostAddresses);

                        webSocketClientManager.connect(
                                transferCode,
                                "sender",
                                localhostAddresses, // Connect to localhost with actual port
                                msg -> logger.info("Sender connection status: {}", msg),
                                err -> logger.error("Sender connection error: {}", err),
                                url -> logger.info("Sender WebSocket open: {}", url),
                                reason -> logger.info("Sender WebSocket closed: {}", reason),
                                msg -> handleIncomingMessage(transferCode, msg),
                                bytes -> handleIncomingBinary(transferCode, bytes)).thenAccept(connResult -> {
                                    activeClients.put(transferCode, connResult.client);
                                    logger.info("Sender connected to WebSocket server for transfer code: {}",
                                            transferCode);

                                    // Create and store the transfer session
                                    TransferSession session = new TransferSession(
                                            transferCode,
                                            senderInfo,
                                            null, // receiver info not available yet
                                            fileName,
                                            fileSize);
                                    activeSessions.put(transferCode, session);

                                    // Register the callback for when receiver connects
                                    registerReceiverConnectionCallback(transferCode, () -> {
                                        logger.info("Receiver connection callback triggered for transfer code: {}",
                                                transferCode);
                                        TransferSession currentSession = activeSessions.get(transferCode);
                                        if (currentSession != null) {
                                            String currentFileName = currentSession.getFileName();
                                            long currentFileSize = currentSession.getFileSize();
                                            showTransferConfirmationDialog(transferCode, currentFileName,
                                                    currentFileSize);
                                        } else {
                                            logger.warn("No transfer session found for code: {}", transferCode);
                                        }
                                    });

                                    future.complete(session);
                                }).exceptionally(ex -> {
                                    logger.error("Failed to connect sender to WebSocket server: {}", ex.getMessage(),
                                            ex);
                                    // Still complete the future with a session, but log the error
                                    TransferSession session = new TransferSession(
                                            transferCode,
                                            senderInfo,
                                            null,
                                            fileName,
                                            fileSize);
                                    activeSessions.put(transferCode, session);
                                    future.complete(session);
                                    return null;
                                });
                    } catch (Exception e) {
                        logger.error("Exception while connecting sender to WebSocket server: {}", e.getMessage(), e);
                        // Still complete the future with a session, but log the error
                        TransferSession session = new TransferSession(
                                transferCode,
                                senderInfo,
                                null,
                                fileName,
                                fileSize);
                        activeSessions.put(transferCode, session);
                        future.complete(session);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error registering sender: {}", ex.getMessage(), ex);
                    future.completeExceptionally(new RuntimeException("Failed to register sender: " + ex.getMessage()));
                    return null;
                });

        return future;
    }

    @Override
    public CompletableFuture<TransferSession> connectToTransfer(String transferCode, String username) {
        logger.info("Connecting to transfer with code: {}", transferCode);
        CompletableFuture<TransferSession> future = new CompletableFuture<>();
        Consumer<String> statusUpdater = msg -> {
            logger.info("Connection status: {}", msg);
            // Only show toast for important status messages, not every connection attempt
            if (msg.contains("Connected") || msg.contains("Attempting") || msg.contains("Trying")) {
                ToastNotification.show(null, msg, ToastNotification.NotificationType.INFO,
                        javafx.util.Duration.seconds(3), 70);
            }
        };

        Consumer<String> errorHandler = err -> {
            logger.error("Connection error: {}", err);
            // Only show error toast for non-connection-refused errors to avoid spam
            if (!err.contains("No route to host") && !err.contains("Connection refused")) {
                ToastNotification.show(null, "Connection error: " + err, ToastNotification.NotificationType.ERROR,
                        javafx.util.Duration.seconds(3), 70);
            }
        };

        // Try to get the sender's connection details if available
        String senderConnectionDetails = getSenderConnectionDetails(transferCode);
        List<String> peerAddresses = new ArrayList<>();

        if (senderConnectionDetails != null) {
            // senderConnectionDetails contains "ip:port" format
            peerAddresses.add(senderConnectionDetails);
            logger.info("Using sender's connection details for connection: {}", senderConnectionDetails);
        } else {
            // No sender transfer record found - try to discover sender on the network
            logger.info("No sender connection details found for transfer code: {}. Attempting network discovery...",
                    transferCode);

            // Generate possible sender addresses to try
            List<String> possibleAddresses = generatePossibleSenderAddresses(transferCode);
            peerAddresses.addAll(possibleAddresses);

            logger.info("Generated {} possible sender addresses to try: {}", possibleAddresses.size(),
                    possibleAddresses);

            if (possibleAddresses.isEmpty()) {
                logger.error("No possible sender addresses found for transfer code: {}", transferCode);
                ToastNotification.show(null,
                        "Unable to find sender on the network. Please ensure the sender has initiated a transfer with code: "
                                + transferCode,
                        ToastNotification.NotificationType.ERROR,
                        javafx.util.Duration.seconds(5),
                        70);
                future.completeExceptionally(
                        new RuntimeException("No sender addresses found for transfer code: " + transferCode));
                return future;
            }
        }

        webSocketClientManager.connect(
                transferCode,
                "receiver",
                peerAddresses,
                statusUpdater,
                errorHandler,
                url -> {
                    logger.info("WebSocket open: {}", url);
                    // When connection is established, log this important event
                    logger.info("WebSocket connection established successfully for transfer code: {}", transferCode);
                },
                reason -> logger.info("WebSocket closed: {}", reason),
                msg -> handleIncomingMessage(transferCode, msg),
                bytes -> handleIncomingBinary(transferCode, bytes)).thenAccept(connResult -> {
                    logger.info("Connection result received for transfer code {}: {}", transferCode, connResult.type);
                    activeClients.put(transferCode, connResult.client);

                    // Register receiver in the WebSocket service
                    WebSocketService.ReceiverInfo receiverInfo = new WebSocketService.ReceiverInfo(
                            username, // username
                            java.util.UUID.randomUUID().toString(), // generate a session ID
                            connResult.type.toString() // connection type
                    );

                    try {
                        // Register the receiver and get the session
                        webSocketService.registerReceiver(transferCode, receiverInfo);

                        // Get the session data from the WebSocket service
                        TransferSession session = webSocketService.getActiveSessions().get(transferCode);

                        if (session != null) {
                            logger.info("Successfully registered receiver for transfer code: {}", transferCode);
                            future.complete(session);
                        } else {
                            // Create a basic session with the transfer code if the WebSocket service
                            // doesn't have one yet
                            logger.info("No session found for transfer code: {}, creating a basic session",
                                    transferCode);
                            TransferSession basicSession = new TransferSession(
                                    transferCode,
                                    null, // senderInfo may be null initially
                                    receiverInfo,
                                    null, // fileName may be null initially
                                    0L // fileSize may be 0 initially
                            );
                            future.complete(basicSession);
                        }
                    } catch (Exception e) {
                        logger.error("Error registering receiver for transfer code: {}", transferCode, e);
                        future.completeExceptionally(
                                new RuntimeException("Error registering receiver: " + e.getMessage()));
                    }
                }).exceptionally(ex -> {
                    logger.error("Connection failed for transfer code: {}", transferCode, ex);
                    future.completeExceptionally(ex);
                    return null;
                });

        return future;
    }

    @Override
    public CompletableFuture<Void> startFileTransfer(String transferCode,
            Consumer<TransferProgress> progressCallback,
            Consumer<TransferComplete> completionCallback) {
        logger.info("Starting file transfer for code: {}", transferCode);

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            TransferSession session = activeSessions.get(transferCode);
            if (session == null) {
                throw new IllegalStateException("Transfer session not found");
            }

            // Get sender transfer records for this session
            List<SenderTransfer> transfers = senderTransferRepository
                    .findBySessionIdOrderByStartTimeDesc(session.getSender().getSessionId());

            if (transfers.isEmpty()) {
                throw new IllegalStateException("No files found for transfer");
            }

            // Register callbacks
            webSocketService.registerProgressCallback(transferCode, progress -> {
                // Use transferCode to look up file info if needed
                List<SenderTransfer> transfersForCode = senderTransferRepository
                        .findBySessionIdOrderByStartTimeDesc(session.getSender().getSessionId());
                String fileName = !transfersForCode.isEmpty() ? transfersForCode.get(0).getFileName() : "Unknown";
                long totalBytes = !transfersForCode.isEmpty() ? transfersForCode.get(0).getFileSize()
                        : progress.getBytesTransferred();
                TransferProgress transferProgress = new TransferProgress(
                        transferCode,
                        fileName,
                        progress.getProgress(),
                        progress.getBytesTransferred(),
                        totalBytes);
                progressCallback.accept(transferProgress);
            });

            webSocketService.registerCompletionCallback(transferCode, complete -> {
                List<SenderTransfer> transfersForCode = senderTransferRepository
                        .findBySessionIdOrderByStartTimeDesc(session.getSender().getSessionId());
                String fileName = !transfersForCode.isEmpty() ? transfersForCode.get(0).getFileName() : "Unknown";
                String checksum = !transfersForCode.isEmpty() ? transfersForCode.get(0).getChecksum() : null;
                TransferComplete transferComplete = new TransferComplete(
                        transferCode,
                        fileName,
                        complete.isSuccess(),
                        complete.getErrorMessage(),
                        checksum);
                completionCallback.accept(transferComplete);
            });

            // Start transferring each file
            transferFiles(transferCode, transfers, future);

        } catch (Exception e) {
            logger.error("Error starting file transfer for code: {}", transferCode, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private void transferFiles(String transferCode, List<SenderTransfer> transfers, CompletableFuture<Void> future) {
        try {
            // Real file transfer implementation
            CompletableFuture.runAsync(() -> {
                try {
                    for (SenderTransfer transfer : transfers) {
                        // Update transfer status
                        transfer.setTransferStatus(SenderTransfer.TransferStatus.TRANSFERRING);
                        senderTransferRepository.save(transfer);

                        // Read the encrypted file
                        String encryptedFilePath = transfer.getFilePath();
                        if (encryptedFilePath == null) {
                            // If no encrypted file path, look for .enc file
                            String originalFileName = transfer.getFileName();
                            String encryptedFileName = originalFileName + ".enc";
                            encryptedFilePath = findEncryptedFile(originalFileName, encryptedFileName);
                        }

                        if (encryptedFilePath != null) {
                            File encryptedFile = new File(encryptedFilePath);
                            if (encryptedFile.exists()) {
                                // Send file through WebSocket (Streaming)
                                webSocketService.sendFile(transferCode, encryptedFile, transfer.getFileName(),
                                        transfer.getFileSize());

                                // Update transfer status to completed
                                transfer.setTransferStatus(SenderTransfer.TransferStatus.COMPLETED);
                                transfer.setEndTime(LocalDateTime.now());
                                senderTransferRepository.save(transfer);

                                logger.info("File transferred successfully: {}", transfer.getFileName());
                            } else {
                                throw new FileNotFoundException("Encrypted file not found: " + encryptedFilePath);
                            }
                        } else {
                            throw new FileNotFoundException(
                                    "Could not locate encrypted file for: " + transfer.getFileName());
                        }
                    }

                    future.complete(null);

                } catch (Exception e) {
                    logger.error("Error during file transfer for code: {}", transferCode, e);
                    future.completeExceptionally(e);
                }
            });

        } catch (Exception e) {
            logger.error("Error initiating file transfer for code: {}", transferCode, e);
            future.completeExceptionally(e);
        }
    }

    private String findEncryptedFile(String originalFileName, String encryptedFileName) {
        // Look for encrypted file in common locations
        String[] searchPaths = {
                System.getProperty("user.home") + "/Downloads/",
                System.getProperty("user.dir") + "/",
                System.getProperty("user.dir") + "/data/",
                System.getProperty("user.dir") + "/temp/"
        };

        for (String path : searchPaths) {
            File file = new File(path + encryptedFileName);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    @Override
    public CompletableFuture<File> saveReceivedFile(String transferCode, String fileName, byte[] fileData,
            File targetDirectory) {
        logger.info("Saving received file: {} for transfer code: {}", fileName, transferCode);

        CompletableFuture<File> future = new CompletableFuture<>();

        try {
            // Create target directory if it doesn't exist
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs();
            }

            // Generate unique filename to avoid conflicts
            String uniqueFileName = generateUniqueFileName(fileName, targetDirectory);
            File targetFile = new File(targetDirectory, uniqueFileName);

            // Write file data
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(fileData);
            }

            // Calculate checksum
            String checksum = calculateFileChecksum(fileData);

            // Update receiver transfer record
            List<ReceiverTransfer> transfers = receiverTransferRepository
                    .findBySessionIdOrderByReceivedTimeDesc(transferCode);
            if (!transfers.isEmpty()) {
                ReceiverTransfer transfer = transfers.get(0);
                transfer.setTransferStatus(ReceiverTransfer.TransferStatus.SAVED);
                transfer.setSavedTime(LocalDateTime.now());
                transfer.setFilePath(targetFile.getAbsolutePath());
                transfer.setChecksum(checksum);
                transfer.setAutoSaved(true);
                receiverTransferRepository.save(transfer);
            }

            logger.info("File saved successfully: {}", targetFile.getAbsolutePath());
            future.complete(targetFile);

        } catch (Exception e) {
            logger.error("Error saving received file: {} for transfer code: {}", fileName, transferCode, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public List<SenderTransfer> getSenderTransferHistory(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return senderTransferRepository.findBySessionIdOrderByStartTimeDesc(sessionId);
        } else {
            // Return recent transfers (last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            return senderTransferRepository.findRecentTransfers(thirtyDaysAgo);
        }
    }

    @Override
    public List<ReceiverTransfer> getReceiverTransferHistory(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return receiverTransferRepository.findBySessionIdOrderByReceivedTimeDesc(sessionId);
        } else {
            // Return recent transfers (last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            return receiverTransferRepository.findRecentTransfers(thirtyDaysAgo);
        }
    }

    @Override
    public void cancelTransfer(String transferCode) {
        logger.info("Cancelling transfer: {}", transferCode);

        try {
            // Update transfer status
            List<SenderTransfer> senderTransfers = senderTransferRepository
                    .findBySessionIdOrderByStartTimeDesc(transferCode);
            for (SenderTransfer transfer : senderTransfers) {
                transfer.setTransferStatus(SenderTransfer.TransferStatus.CANCELLED);
                transfer.setEndTime(LocalDateTime.now());
                senderTransferRepository.save(transfer);
            }

            List<ReceiverTransfer> receiverTransfers = receiverTransferRepository
                    .findBySessionIdOrderByReceivedTimeDesc(transferCode);
            for (ReceiverTransfer transfer : receiverTransfers) {
                transfer.setTransferStatus(ReceiverTransfer.TransferStatus.CANCELLED);
                receiverTransferRepository.save(transfer);
            }

            // Disconnect from WebSocket service
            webSocketService.disconnect(transferCode, WebSocketService.DeviceType.SENDER);
            webSocketService.disconnect(transferCode, WebSocketService.DeviceType.RECEIVER);

            // Remove from active sessions
            activeSessions.remove(transferCode);

        } catch (Exception e) {
            logger.error("Error cancelling transfer: {}", transferCode, e);
        }
    }

    @Override
    public boolean isTransferActive(String transferCode) {
        return activeSessions.containsKey(transferCode) || webSocketService.isTransferActive(transferCode);
    }

    @Override
    public List<TransferSession> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    @Override
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupCompletedTransfers() {
        logger.debug("Cleaning up completed transfers");

        try {
            // Clean up old transfer records (older than 90 days)
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            senderTransferRepository.deleteOldTransfers(cutoffDate);
            receiverTransferRepository.deleteOldTransfers(cutoffDate);

            // Clean up received files from memory
            receivedFiles.clear();

        } catch (Exception e) {
            logger.error("Error during transfer cleanup", e);
        }
    }

    // Helper methods
    private String generateUniqueFileName(String originalName, File directory) {
        String baseName = originalName;
        String extension = "";

        int lastDot = originalName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = originalName.substring(0, lastDot);
            extension = originalName.substring(lastDot);
        }

        String fileName = baseName + extension;
        File file = new File(directory, fileName);

        int counter = 1;
        while (file.exists()) {
            fileName = baseName + " (" + counter + ")" + extension;
            file = new File(directory, fileName);
            counter++;
        }

        return fileName;
    }

    private String calculateFileChecksum(byte[] fileData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileData);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Error calculating file checksum", e);
            return null;
        }
    }

    /**
     * Calculates SHA-256 checksum for a file for integrity verification
     * 
     * @param file the file to calculate checksum for
     * @return the hexadecimal string representation of the checksum
     * @throws IOException              if an I/O error occurs
     * @throws NoSuchAlgorithmException if SHA-256 algorithm is not available
     */
    private String calculateChecksum(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file);
                DigestInputStream digestInputStream = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInputStream.read(buffer) != -1) {
                // Read the entire file through the digest stream
            }
            byte[] digestBytes = digest.digest();
            // Convert to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : digestBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    /**
     * Calculates the total size of all files
     * 
     * @param files list of files
     * @return total size in bytes
     */
    private long calculateTotalFileSize(List<File> files) {
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        return totalSize;
    }

    // Handle incoming text message
    private void handleIncomingMessage(String transferCode, String msg) {
        try {
            JsonNode root = objectMapper.readTree(msg);
            String type = root.path("type").asText("");
            switch (type) {
                case "progress" -> {
                    double progress = root.path("progress").asDouble(0.0);
                    long bytesTransferred = root.path("bytesTransferred").asLong(0);
                    String fileName = root.path("fileName").asText("");

                    // Update progress in our tracking map
                    TransferProgress progressInfo = new TransferProgress(
                            transferCode,
                            fileName,
                            progress,
                            bytesTransferred,
                            root.path("totalBytes").asLong(bytesTransferred));
                    transferProgress.put(transferCode, progressInfo);

                    // Log progress update
                    logger.info("Progress update for {}: {}% ({}/{} bytes)",
                            fileName, Math.round(progress * 100),
                            bytesTransferred, progressInfo.getTotalBytes());
                }
                case "complete" -> {
                    String fileName = root.path("fileName").asText("");
                    String checksum = root.path("checksum").asText("");
                    boolean success = root.path("success").asBoolean(false);
                    String errorMessage = root.path("errorMessage").asText("");

                    // Update transfer status in database
                    if (success) {
                        List<SenderTransfer> transfers = senderTransferRepository
                                .findByReceiverCodeOrderByStartTimeDesc(transferCode);
                        for (SenderTransfer transfer : transfers) {
                            if (transfer.getFileName().equals(fileName)) {
                                transfer.setTransferStatus(SenderTransfer.TransferStatus.COMPLETED);
                                transfer.setEndTime(LocalDateTime.now());
                                senderTransferRepository.save(transfer);
                                break;
                            }
                        }

                        logger.info("Transfer complete: {} (success: true, checksum: {})", fileName, checksum);
                        ToastNotification.show(null,
                                "Transfer complete: " + fileName,
                                ToastNotification.NotificationType.SUCCESS,
                                javafx.util.Duration.seconds(5),
                                70);
                    } else {
                        logger.error("Transfer failed: {} (Error: {})", fileName, errorMessage);
                        ToastNotification.show(null,
                                "Transfer failed: " + errorMessage,
                                ToastNotification.NotificationType.ERROR,
                                javafx.util.Duration.seconds(5),
                                70);
                    }
                }
                case "ready" -> {
                    logger.info("Receiver is ready to receive files for transfer code: {}", transferCode);
                    // Could trigger file sending here if using a pull model
                }
                case "peerConnected" -> {
                    String role = root.path("role").asText("");
                    logger.info("Received peerConnected message for transfer code: {} with role: {}", transferCode,
                            role);
                    if ("receiver".equals(role)) {
                        logger.info("Receiver connected for transfer code: {}", transferCode);
                        // Trigger the receiver connection callback
                        Runnable callback = receiverConnectionCallbacks.get(transferCode);
                        if (callback != null) {
                            logger.info("Executing receiver connection callback for transfer code: {}", transferCode);
                            callback.run();
                        } else {
                            logger.warn("No receiver connection callback registered for transfer code: {}",
                                    transferCode);
                        }
                    }
                }
                default -> logger.info("Received message: {}", msg);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse incoming message as JSON: {}", msg);
        }
    }

    // Handle incoming binary data (file chunk)
    private void handleIncomingBinary(String transferCode, java.nio.ByteBuffer bytes) {
        try {
            ByteArrayOutputStream buffer = incomingFileBuffers.computeIfAbsent(transferCode,
                    k -> new ByteArrayOutputStream());
            byte[] chunk = new byte[bytes.remaining()];
            bytes.get(chunk);
            buffer.write(chunk);

            int chunkSize = chunk.length;
            logger.info("Received file chunk ({} bytes) for transfer {}", chunkSize, transferCode);

            // Look for EOF marker in the last 8 bytes of the chunk
            // Check if the last 8 bytes contain our EOF marker sequence
            if (chunkSize >= 8) {
                // Extract and check the last 8 bytes
                byte[] lastBytes = new byte[8];
                System.arraycopy(chunk, chunkSize - 8, lastBytes, 0, 8);

                // Check if this is our EOF marker (using a simple known sequence for example)
                String marker = new String(lastBytes);
                if ("EOF_MARK".equals(marker)) {
                    // This is the end of file, process the complete file
                    logger.info("EOF marker detected, processing complete file for transfer {}", transferCode);

                    // Remove the EOF marker from the buffer
                    byte[] completeFileData = buffer.toByteArray();
                    byte[] fileDataWithoutMarker = new byte[completeFileData.length - 8];
                    System.arraycopy(completeFileData, 0, fileDataWithoutMarker, 0, completeFileData.length - 8);

                    // Store the complete file data
                    receivedFiles.put(transferCode, fileDataWithoutMarker);

                    // Look up session info to get filename
                    TransferSession session = activeSessions.get(transferCode);
                    if (session != null) {
                        String fileName = session.getFileName();

                        // Create a ReceiverTransfer record
                        ReceiverTransfer transfer = new ReceiverTransfer();
                        transfer.setSessionId(transferCode);
                        transfer.setFileName(fileName);
                        transfer.setFileSize((long) fileDataWithoutMarker.length);
                        transfer.setReceivedTime(LocalDateTime.now());
                        transfer.setTransferStatus(ReceiverTransfer.TransferStatus.RECEIVED);

                        // Calculate checksum for verification
                        String checksum = calculateFileChecksum(fileDataWithoutMarker);
                        transfer.setChecksum(checksum);

                        // Save to repository
                        receiverTransferRepository.save(transfer);

                        logger.info("File received successfully: {} ({} bytes)", fileName,
                                fileDataWithoutMarker.length);

                        // Notify user that file has been received and is ready for saving
                        ToastNotification.show(null,
                                "File received: " + fileName,
                                ToastNotification.NotificationType.SUCCESS,
                                javafx.util.Duration.seconds(5),
                                70);
                    }

                    // Clear the buffer for potential next file
                    incomingFileBuffers.remove(transferCode);
                }
            }

        } catch (Exception e) {
            logger.error("Error processing received file chunk for transfer {}: {}", transferCode, e.getMessage());
            ToastNotification.show(null,
                    "Error receiving file: " + e.getMessage(),
                    ToastNotification.NotificationType.ERROR,
                    javafx.util.Duration.seconds(5),
                    70);
        }
    }

    // Add a method to discover local LAN IPs
    private List<String> discoverLocalLANAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp())
                    continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to discover LAN IPs: {}", e.getMessage());
        }
        return ips;
    }

    /**
     * Generate possible sender addresses to try when connecting to a transfer
     * This method tries common LAN IP ranges and the local machine's network
     */
    private List<String> generatePossibleSenderAddresses(String transferCode) {
        List<String> addresses = new ArrayList<>();

        try {
            // Get the local IP to determine the network range
            Optional<String> localIp = NetworkUtils.getLocalIpAddress();
            if (localIp.isPresent()) {
                String localIpStr = localIp.get();
                logger.info("Local IP detected: {}", localIpStr);

                // Extract network prefix (e.g., "192.168.1" from "192.168.1.101")
                String[] parts = localIpStr.split("\\.");
                if (parts.length == 4) {
                    String networkPrefix = parts[0] + "." + parts[1] + "." + parts[2];
                    logger.info("Network prefix detected: {}", networkPrefix);

                    // Try common ports for WebSocket server
                    List<Integer> commonPorts = Arrays.asList(8445, 8446, 8447, 8448, 8449, 8450);

                    // Generate addresses in the same network range, but limit to a smaller range
                    // Try IPs close to our own IP first (more likely to be on same network)
                    int ownLastOctet = Integer.parseInt(parts[3]);

                    // Try IPs in a range around our own IP (±20)
                    for (int offset = -20; offset <= 20; offset++) {
                        int candidateLastOctet = ownLastOctet + offset;
                        if (candidateLastOctet >= 1 && candidateLastOctet <= 254) {
                            String candidateIp = networkPrefix + "." + candidateLastOctet;
                            // Skip our own IP
                            if (!candidateIp.equals(localIpStr)) {
                                for (int port : commonPorts) {
                                    addresses.add(candidateIp + ":" + port);
                                }
                            }
                        }
                    }

                    logger.info("Generated {} possible sender addresses in network range {} (around IP {})",
                            addresses.size(), networkPrefix, localIpStr);
                }
            }

            // Also try some common LAN ranges if we couldn't determine the local network
            if (addresses.isEmpty()) {
                logger.info("Could not determine local network, trying common LAN ranges");
                List<String> commonRanges = Arrays.asList(
                        "192.168.1", "192.168.0", "192.168.2");

                List<Integer> commonPorts = Arrays.asList(8445, 8446, 8447, 8448, 8449, 8450);

                // Only try a few IPs from each range to avoid overwhelming
                for (String range : commonRanges) {
                    for (int i = 1; i <= 50; i++) { // Limit to first 50 IPs
                        String candidateIp = range + "." + i;
                        for (int port : commonPorts) {
                            addresses.add(candidateIp + ":" + port);
                        }
                    }
                }

                logger.info("Generated {} possible sender addresses using common LAN ranges", addresses.size());
            }

            // Limit the number of addresses to try to avoid overwhelming the system
            if (addresses.size() > 100) {
                logger.info("Limiting addresses to first 100 to avoid overwhelming the system");
                addresses = addresses.subList(0, 100);
            }

        } catch (Exception e) {
            logger.warn("Error generating possible sender addresses: {}", e.getMessage());
        }

        return addresses;
    }

    // Store sender connection details
    private final Map<String, String> senderConnectionDetails = new ConcurrentHashMap<>();

    // Store receiver connection callbacks
    private final Map<String, Runnable> receiverConnectionCallbacks = new ConcurrentHashMap<>();

    @Override
    public void storeSenderConnectionDetails(String transferCode, String ip, int port) {
        String details = ip + ":" + port;
        senderConnectionDetails.put(transferCode, details);
        logger.info("Stored sender connection details for {}: {}", transferCode, details);

        // Also store in database for cross-machine access
        try {
            List<SenderTransfer> transfers = senderTransferRepository
                    .findByReceiverCodeOrderByStartTimeDesc(transferCode);
            for (SenderTransfer transfer : transfers) {
                transfer.setSenderIp(ip);
                transfer.setSenderPort(port);
                senderTransferRepository.save(transfer);
                logger.info("Stored sender connection details in database for transfer: {}", transferCode);
            }
        } catch (Exception e) {
            logger.warn("Failed to store sender connection details in database: {}", e.getMessage());
        }
    }

    /**
     * Store the sender's local IP address when initiating a transfer
     */
    private void storeSenderLocalIp(String transferCode) {
        try {
            // Get the local IP address of this device
            Optional<String> localIp = NetworkUtils.getLocalIpAddress();
            if (localIp.isPresent()) {
                String ip = localIp.get();
                senderConnectionDetails.put(transferCode + "_local_ip", ip);
                logger.info("Stored sender local IP for {}: {}", transferCode, ip);
            }
        } catch (Exception e) {
            logger.warn("Failed to store sender local IP: {}", e.getMessage());
        }
    }

    @Override
    public String getSenderConnectionDetails(String transferCode) {
        // First try to get from database for cross-machine access
        try {
            List<SenderTransfer> transfers = senderTransferRepository
                    .findByReceiverCodeOrderByStartTimeDesc(transferCode);
            if (transfers.isEmpty()) {
                logger.warn(
                        "No sender transfer record found in database for transfer code: {} - this means no sender has initiated a transfer with this code yet",
                        transferCode);
                return null;
            }

            for (SenderTransfer transfer : transfers) {
                if (transfer.getSenderIp() != null && transfer.getSenderPort() != null) {
                    String details = transfer.getSenderIp() + ":" + transfer.getSenderPort();
                    logger.info("Retrieved sender connection details from database for {}: {}", transferCode, details);
                    return details;
                }
            }
            logger.warn("Found sender transfer record for code {} but sender_ip or sender_port is null", transferCode);
        } catch (Exception e) {
            logger.warn("Failed to retrieve sender connection details from database: {}", e.getMessage());
        }

        // Fall back to in-memory storage
        String details = senderConnectionDetails.get(transferCode);
        if (details != null) {
            logger.info("Retrieved sender connection details from memory for {}: {}", transferCode, details);
        } else {
            logger.warn("No sender connection details found for transfer code: {}", transferCode);
        }
        return details;
    }

    /**
     * Get the sender's local IP address for a transfer code
     */
    public String getSenderLocalIp(String transferCode) {
        String localIp = senderConnectionDetails.get(transferCode + "_local_ip");
        if (localIp != null) {
            logger.info("Retrieved sender local IP for {}: {}", transferCode, localIp);
        } else {
            logger.warn("No sender local IP found for transfer code: {}", transferCode);
        }
        return localIp;
    }

    @Override
    public void registerReceiverConnectionCallback(String transferCode, Runnable callback) {
        receiverConnectionCallbacks.put(transferCode, callback);
        logger.info("Registered receiver connection callback for transfer code: {}", transferCode);
    }

    @Override
    public void connectSenderWebSocketClient(String transferCode, String username) {
        // No-op in callback-based design
    }

    private void showTransferConfirmationDialog(String transferCode, String fileName, long fileSize) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Receiver Connected");
        alert.setHeaderText("Receiver is ready to receive file");
        alert.setContentText(String.format("File: %s\nSize: %s\n\nDo you want to start the transfer?",
                fileName, formatFileSize(fileSize)));

        ButtonType startButton = new ButtonType("Start Transfer");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(startButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == startButton) {
                logger.info("User confirmed transfer start for code: {}", transferCode);
                // Start the file transfer
                startFileTransfer(transferCode,
                        progress -> {
                            // Update progress
                            logger.info("Transfer progress: {}%", progress.getProgress() * 100);
                        },
                        completion -> {
                            if (completion.isSuccess()) {
                                logger.info("Transfer completed successfully for code: {}", transferCode);
                                ToastNotification.show(null, "File transfer completed successfully!",
                                        ToastNotification.NotificationType.SUCCESS, javafx.util.Duration.seconds(5),
                                        70);
                            } else {
                                logger.error("Transfer failed for code {}: {}", transferCode,
                                        completion.getErrorMessage());
                                ToastNotification.show(null, "Transfer failed: " + completion.getErrorMessage(),
                                        ToastNotification.NotificationType.ERROR, javafx.util.Duration.seconds(5), 70);
                            }
                        });
            } else {
                logger.info("User cancelled transfer for code: {}", transferCode);
                ToastNotification.show(null, "Transfer cancelled by user",
                        ToastNotification.NotificationType.INFO, javafx.util.Duration.seconds(3), 70);
            }
        });
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}