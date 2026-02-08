package com.securetransfer.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for WebSocket-based secure file transfer communications.
 * Handles device connections, transfer coordination, and file relay.
 */
public interface WebSocketService {

    /**
     * Register a sender device with a transfer code
     * 
     * @param transferCode 6-digit transfer code
     * @param senderInfo   sender device information
     * @return CompletableFuture that completes when registration is successful
     */
    CompletableFuture<Void> registerSender(String transferCode, SenderInfo senderInfo, String fileName, long fileSize);

    /**
     * Register a receiver device with a transfer code
     * 
     * @param transferCode 6-digit transfer code
     * @param receiverInfo receiver device information
     * @return CompletableFuture that completes when connection is established
     */
    CompletableFuture<Void> registerReceiver(String transferCode, ReceiverInfo receiverInfo);

    /**
     * Send encrypted file data to receiver (streaming from file)
     * 
     * @param transferCode transfer code for the session
     * @param file         encrypted file
     * @param fileName     original file name
     * @param fileSize     file size in bytes
     * @return CompletableFuture that completes when file is sent
     */
    CompletableFuture<Void> sendFile(String transferCode, java.io.File file, String fileName, long fileSize);

    /**
     * Send encrypted file data to receiver (legacy byte array)
     * 
     * @param transferCode transfer code for the session
     * @param fileData     encrypted file data
     * @param fileName     original file name
     * @param fileSize     file size in bytes
     * @return CompletableFuture that completes when file is sent
     */
    @Deprecated
    CompletableFuture<Void> sendFile(String transferCode, byte[] fileData, String fileName, long fileSize);

    /**
     * Send transfer progress update
     * 
     * @param transferCode     transfer code for the session
     * @param progress         progress percentage (0.0 to 1.0)
     * @param bytesTransferred bytes transferred so far
     */
    void sendProgress(String transferCode, double progress, long bytesTransferred);

    /**
     * Send transfer completion notification
     * 
     * @param transferCode transfer code for the session
     * @param success      whether transfer was successful
     * @param errorMessage error message if transfer failed
     */
    void sendTransferComplete(String transferCode, boolean success, String errorMessage);

    /**
     * Disconnect a device from the transfer session
     * 
     * @param transferCode transfer code for the session
     * @param deviceType   type of device (SENDER or RECEIVER)
     */
    void disconnect(String transferCode, DeviceType deviceType);

    /**
     * Check if a transfer code is active
     * 
     * @param transferCode transfer code to check
     * @return true if transfer code is active
     */
    boolean isTransferActive(String transferCode);

    /**
     * Get active transfer sessions
     * 
     * @return Map of transfer codes to session info
     */
    Map<String, TransferSession> getActiveSessions();

    /**
     * Clean up expired transfer sessions
     */
    void cleanupExpiredSessions();

    /**
     * Register progress callback for a transfer
     * 
     * @param transferCode transfer code
     * @param callback     progress callback
     */
    void registerProgressCallback(String transferCode, java.util.function.Consumer<TransferProgress> callback);

    /**
     * Register completion callback for a transfer
     * 
     * @param transferCode transfer code
     * @param callback     completion callback
     */
    void registerCompletionCallback(String transferCode, java.util.function.Consumer<TransferComplete> callback);

    /**
     * Transfer progress information
     */
    class TransferProgress {
        private final String transferCode;
        private final double progress;
        private final long bytesTransferred;

        public TransferProgress(String transferCode, double progress, long bytesTransferred) {
            this.transferCode = transferCode;
            this.progress = progress;
            this.bytesTransferred = bytesTransferred;
        }

        public String getTransferCode() {
            return transferCode;
        }

        public double getProgress() {
            return progress;
        }

        public long getBytesTransferred() {
            return bytesTransferred;
        }
    }

    /**
     * Transfer completion information
     */
    class TransferComplete {
        private final String transferCode;
        private final boolean success;
        private final String errorMessage;

        public TransferComplete(String transferCode, boolean success, String errorMessage) {
            this.transferCode = transferCode;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getTransferCode() {
            return transferCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Device types for transfer sessions
     */
    enum DeviceType {
        SENDER, RECEIVER
    }

    /**
     * Transfer session information
     */
    class TransferSession {
        private final String transferCode;
        private final SenderInfo sender;
        private final ReceiverInfo receiver;
        private final long startTime;
        private TransferStatus status;
        private org.java_websocket.WebSocket senderWebSocket;
        private org.java_websocket.WebSocket receiverWebSocket;
        private String fileName;
        private long fileSize;

        public TransferSession(String transferCode, SenderInfo sender, ReceiverInfo receiver, String fileName,
                long fileSize) {
            this.transferCode = transferCode;
            this.sender = sender;
            this.receiver = receiver;
            this.startTime = System.currentTimeMillis();
            this.status = TransferStatus.CONNECTING;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }

        // Overload for legacy usage
        public TransferSession(String transferCode, SenderInfo sender, ReceiverInfo receiver) {
            this(transferCode, sender, receiver, null, 0L);
        }

        // Getters and setters
        public String getTransferCode() {
            return transferCode;
        }

        public SenderInfo getSender() {
            return sender;
        }

        public ReceiverInfo getReceiver() {
            return receiver;
        }

        public long getStartTime() {
            return startTime;
        }

        public TransferStatus getStatus() {
            return status;
        }

        public void setStatus(TransferStatus status) {
            this.status = status;
        }

        public org.java_websocket.WebSocket getSenderWebSocket() {
            return senderWebSocket;
        }

        public void setSenderWebSocket(org.java_websocket.WebSocket senderWebSocket) {
            this.senderWebSocket = senderWebSocket;
        }

        public org.java_websocket.WebSocket getReceiverWebSocket() {
            return receiverWebSocket;
        }

        public void setReceiverWebSocket(org.java_websocket.WebSocket receiverWebSocket) {
            this.receiverWebSocket = receiverWebSocket;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }
    }

    /**
     * Transfer status enumeration
     */
    enum TransferStatus {
        CONNECTING, CONNECTED, TRANSFERRING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Sender device information
     */
    class SenderInfo {
        private final String deviceId;
        private final String username;
        private final String sessionId;

        public SenderInfo(String deviceId, String username, String sessionId) {
            this.deviceId = deviceId;
            this.username = username;
            this.sessionId = sessionId;
        }

        // Getters
        public String getDeviceId() {
            return deviceId;
        }

        public String getUsername() {
            return username;
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * Receiver device information
     */
    class ReceiverInfo {
        private final String deviceId;
        private final String username;
        private final String sessionId;

        public ReceiverInfo(String deviceId, String username, String sessionId) {
            this.deviceId = deviceId;
            this.username = username;
            this.sessionId = sessionId;
        }

        // Getters
        public String getDeviceId() {
            return deviceId;
        }

        public String getUsername() {
            return username;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}