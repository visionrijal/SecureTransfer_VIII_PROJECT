package com.securetransfer.service.impl;

import com.securetransfer.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Implementation of WebSocket service for secure file transfer.
 * Manages transfer sessions, device connections, and file relay.
 */
@Service
public class WebSocketServiceImpl implements WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServiceImpl.class);

    @Autowired
    private SecureTransferWebSocketServer webSocketServer;

    // Transfer session management
    private final Map<String, TransferSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> pendingConnections = new ConcurrentHashMap<>();

    // Transfer progress callbacks
    private final Map<String, Consumer<WebSocketService.TransferProgress>> progressCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Consumer<WebSocketService.TransferComplete>> completionCallbacks = new ConcurrentHashMap<>();

    // Connection timeout (30 minutes)
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    // File transfer chunk size (1MB)
    private static final int CHUNK_SIZE = 1024 * 1024;

    @Override
    public CompletableFuture<Void> registerSender(String transferCode, SenderInfo senderInfo, String fileName,
            long fileSize) {
        logger.info("registerSender called for transfer code: {}", transferCode);

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            // Check if transfer code already exists
            if (activeSessions.containsKey(transferCode)) {
                logger.warn("Transfer code {} already exists", transferCode);
                future.completeExceptionally(new IllegalStateException("Transfer code already in use"));
                return future;
            }

            // Create new transfer session
            TransferSession session = new TransferSession(transferCode, senderInfo, null, fileName, fileSize);
            session.setStatus(TransferStatus.CONNECTING);
            activeSessions.put(transferCode, session);
            logger.info("registerSender completing future for transfer code: {}", transferCode);
            future.complete(null);
        } catch (Exception e) {
            logger.error("Error in registerSender for transfer code {}: {}", transferCode, e.getMessage(), e);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> registerReceiver(String transferCode, ReceiverInfo receiverInfo) {
        logger.info("Registering receiver for transfer code: {}", transferCode);

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            // Check if transfer code exists
            TransferSession session = activeSessions.get(transferCode);
            if (session == null) {
                logger.warn("Transfer code {} not found", transferCode);
                future.completeExceptionally(new IllegalArgumentException("Invalid transfer code"));
                return future;
            }

            // Update session with receiver info
            session = new TransferSession(transferCode, session.getSender(), receiverInfo, session.getFileName(),
                    session.getFileSize());
            session.setStatus(TransferStatus.CONNECTED);
            activeSessions.put(transferCode, session);

            // Complete receiver connection
            future.complete(null);

            logger.info("Receiver registered successfully for transfer code: {}", transferCode);

        } catch (Exception e) {
            logger.error("Failed to register receiver for transfer code: {}", transferCode, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> sendFile(String transferCode, File file, String fileName, long fileSize) {
        logger.info("Streaming file: {} ({} bytes) for transfer code: {}", fileName, fileSize, transferCode);

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            TransferSession session = activeSessions.get(transferCode);
            if (session == null) {
                throw new IllegalStateException("Transfer session not found");
            }

            if (session.getStatus() != TransferStatus.CONNECTED) {
                throw new IllegalStateException("Transfer session not ready");
            }

            session.setStatus(TransferStatus.TRANSFERRING);

            // Send file in chunks using streaming
            sendFileInChunksStreaming(transferCode, file, fileName, fileSize, future);

        } catch (Exception e) {
            logger.error("Failed to stream file for transfer code: {}", transferCode, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> sendFile(String transferCode, byte[] fileData, String fileName, long fileSize) {
        logger.info("Sending file (byte array): {} ({} bytes) for transfer code: {}", fileName, fileSize, transferCode);

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            TransferSession session = activeSessions.get(transferCode);
            if (session == null) {
                throw new IllegalStateException("Transfer session not found");
            }

            if (session.getStatus() != TransferStatus.CONNECTED) {
                throw new IllegalStateException("Transfer session not ready");
            }

            session.setStatus(TransferStatus.TRANSFERRING);

            // Send file in chunks
            sendFileInChunks(transferCode, fileData, fileName, fileSize, future);

        } catch (Exception e) {
            logger.error("Failed to send file for transfer code: {}", transferCode, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private void sendFileInChunksStreaming(String transferCode, File file, String fileName, long fileSize,
            CompletableFuture<Void> future) {
        try (FileInputStream fis = new FileInputStream(file)) {
            int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;
            long bytesTransferred = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk;
                boolean isLastChunk = (chunkIndex == totalChunks - 1) || (bytesTransferred + bytesRead >= fileSize);

                if (isLastChunk) {
                    // Add EOF marker to the last chunk
                    byte[] eofMarker = "EOF_MARK".getBytes();
                    chunk = new byte[bytesRead + eofMarker.length];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    System.arraycopy(eofMarker, 0, chunk, bytesRead, eofMarker.length);
                    logger.info("Added EOF marker to final chunk {} for transfer code: {}", chunkIndex, transferCode);
                } else {
                    chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                }

                try {
                    sendChunkData(transferCode, chunk, chunkIndex, totalChunks);

                    bytesTransferred += bytesRead;
                    double progress = (double) bytesTransferred / fileSize;

                    sendProgress(transferCode, progress, bytesTransferred);

                    // Small delay to prevent overwhelming the receiver
                    Thread.sleep(10);

                    if (isLastChunk) {
                        TransferSession session = activeSessions.get(transferCode);
                        if (session != null) {
                            session.setStatus(TransferStatus.COMPLETED);
                        }
                        sendTransferComplete(transferCode, true, null);
                        future.complete(null);
                        break;
                    }

                    chunkIndex++;
                } catch (Exception e) {
                    logger.error("Error sending chunk {} for transfer code: {}", chunkIndex, transferCode, e);
                    handleTransferFailure(transferCode, e, future);
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error reading file for transfer code: {}", transferCode, e);
            handleTransferFailure(transferCode, e, future);
        }
    }

    private void handleTransferFailure(String transferCode, Exception e, CompletableFuture<Void> future) {
        TransferSession session = activeSessions.get(transferCode);
        if (session != null) {
            session.setStatus(TransferStatus.FAILED);
        }
        sendTransferComplete(transferCode, false, e.getMessage());
        future.completeExceptionally(e);
    }

    private void sendFileInChunks(String transferCode, byte[] fileData, String fileName, long fileSize,
            CompletableFuture<Void> future) {
        try {
            int totalChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);
            AtomicReference<Integer> completedChunks = new AtomicReference<>(0);

            // Send chunks sequentially to ensure proper order
            for (int i = 0; i < totalChunks; i++) {
                final int chunkIndex = i;
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, fileData.length);
                byte[] chunk;

                // Add EOF marker to the last chunk
                if (i == totalChunks - 1) {
                    // This is the last chunk - add EOF marker
                    byte[] eofMarker = "EOF_MARK".getBytes();
                    chunk = new byte[end - start + eofMarker.length];
                    System.arraycopy(fileData, start, chunk, 0, end - start);
                    System.arraycopy(eofMarker, 0, chunk, end - start, eofMarker.length);
                    logger.info("Added EOF marker to final chunk {} for transfer code: {}", chunkIndex, transferCode);
                } else {
                    // Regular chunk
                    chunk = new byte[end - start];
                    System.arraycopy(fileData, start, chunk, 0, chunk.length);
                }

                try {
                    // Send chunk data through WebSocket connection
                    sendChunkData(transferCode, chunk, chunkIndex, totalChunks);

                    // Update progress
                    int completed = completedChunks.get() + 1;
                    completedChunks.set(completed);
                    double progress = (double) completed / totalChunks;
                    long bytesTransferred = Math.min(completed * CHUNK_SIZE, fileData.length);

                    sendProgress(transferCode, progress, bytesTransferred);

                    // Small delay to prevent overwhelming the receiver
                    Thread.sleep(10);

                    // Check if all chunks completed
                    if (completed == totalChunks) {
                        TransferSession session = activeSessions.get(transferCode);
                        if (session != null) {
                            session.setStatus(TransferStatus.COMPLETED);
                        }
                        sendTransferComplete(transferCode, true, null);
                        future.complete(null);
                    }

                } catch (Exception e) {
                    logger.error("Error sending chunk {} for transfer code: {}", chunkIndex, transferCode, e);
                    TransferSession session = activeSessions.get(transferCode);
                    if (session != null) {
                        session.setStatus(TransferStatus.FAILED);
                    }
                    sendTransferComplete(transferCode, false, e.getMessage());
                    future.completeExceptionally(e);
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Error in chunked file transfer for transfer code: {}", transferCode, e);
            future.completeExceptionally(e);
        }
    }

    private void sendChunkData(String transferCode, byte[] chunkData, int chunkIndex, int totalChunks) {
        logger.debug("Sending chunk {}/{} for transfer code: {} ({} bytes)",
                chunkIndex + 1, totalChunks, transferCode, chunkData.length);

        // Use the real WebSocket server to send the chunk
        TransferSession session = activeSessions.get(transferCode);
        if (session != null && session.getStatus() == TransferStatus.TRANSFERRING) {
            // Send through WebSocket server
            webSocketServer.sendFileChunk(transferCode, chunkData);
            logger.debug("Real WebSocket chunk sent: {} for transfer {}", chunkIndex, transferCode);
        }
    }

    @Override
    public void sendProgress(String transferCode, double progress, long bytesTransferred) {
        logger.debug("Transfer progress for code {}: {}% ({} bytes)", transferCode,
                String.format("%.1f", progress * 100), bytesTransferred);

        Consumer<WebSocketService.TransferProgress> callback = progressCallbacks.get(transferCode);
        if (callback != null) {
            WebSocketService.TransferProgress progressInfo = new WebSocketService.TransferProgress(transferCode,
                    progress, bytesTransferred);
            callback.accept(progressInfo);
        }
    }

    @Override
    public void sendTransferComplete(String transferCode, boolean success, String errorMessage) {
        logger.info("Transfer complete for code {}: success={}, error={}", transferCode, success, errorMessage);

        Consumer<WebSocketService.TransferComplete> callback = completionCallbacks.get(transferCode);
        if (callback != null) {
            WebSocketService.TransferComplete completeInfo = new WebSocketService.TransferComplete(transferCode,
                    success, errorMessage);
            callback.accept(completeInfo);
        }

        // Clean up session after completion
        if (success) {
            activeSessions.remove(transferCode);
        }
    }

    @Override
    public void disconnect(String transferCode, DeviceType deviceType) {
        logger.info("Disconnecting {} for transfer code: {}", deviceType, transferCode);

        TransferSession session = activeSessions.get(transferCode);
        if (session != null) {
            session.setStatus(TransferStatus.CANCELLED);
            activeSessions.remove(transferCode);
        }

        // Cancel pending connection
        CompletableFuture<Void> pendingConnection = pendingConnections.remove(transferCode);
        if (pendingConnection != null) {
            pendingConnection.cancel(true);
        }

        // Clean up callbacks
        progressCallbacks.remove(transferCode);
        completionCallbacks.remove(transferCode);
    }

    @Override
    public boolean isTransferActive(String transferCode) {
        return activeSessions.containsKey(transferCode);
    }

    @Override
    public Map<String, TransferSession> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }

    @Override
    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();

        activeSessions.entrySet().removeIf(entry -> {
            TransferSession session = entry.getValue();
            boolean expired = (currentTime - session.getStartTime()) > SESSION_TIMEOUT_MS;

            if (expired) {
                logger.info("Cleaning up expired session: {}", session.getTransferCode());
                // Clean up callbacks
                progressCallbacks.remove(session.getTransferCode());
                completionCallbacks.remove(session.getTransferCode());
            }

            return expired;
        });

        // Clean up expired pending connections
        pendingConnections.entrySet().removeIf(entry -> {
            CompletableFuture<Void> future = entry.getValue();
            boolean expired = future.isDone() || future.isCancelled();

            if (expired) {
                logger.debug("Cleaning up expired pending connection: {}", entry.getKey());
            }

            return expired;
        });
    }

    // Callback registration methods
    @Override
    public void registerProgressCallback(String transferCode, Consumer<WebSocketService.TransferProgress> callback) {
        progressCallbacks.put(transferCode, callback);
    }

    @Override
    public void registerCompletionCallback(String transferCode, Consumer<WebSocketService.TransferComplete> callback) {
        completionCallbacks.put(transferCode, callback);
    }
}