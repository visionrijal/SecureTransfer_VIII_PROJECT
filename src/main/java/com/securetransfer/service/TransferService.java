package com.securetransfer.service;

import com.securetransfer.model.entity.ReceiverTransfer;
import com.securetransfer.model.entity.SenderTransfer;
import com.securetransfer.service.WebSocketService.TransferSession;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service interface for handling secure file transfer operations.
 * Manages file encryption, transfer coordination, and history tracking.
 */
public interface TransferService {
    
    /**
     * Initialize a file transfer session as sender
     * @param transferCode 6-digit transfer code
     * @param files list of files to transfer
     * @param username sender username
     * @return CompletableFuture that completes when receiver connects
     */
    CompletableFuture<TransferSession> initiateTransfer(String transferCode, List<File> files, String username);
    
    /**
     * Initialize a file transfer session as sender
     * @param transferCode 6-digit transfer code
     * @param files list of files to transfer
     * @param username sender username
     * @param fileName name of the file to transfer
     * @param fileSize size of the file to transfer
     * @return CompletableFuture that completes when receiver connects
     */
    CompletableFuture<TransferSession> initiateTransfer(String transferCode, List<File> files, String username, String fileName, long fileSize);
    
    /**
     * Connect to a transfer session as receiver
     * @param transferCode 6-digit transfer code
     * @param username receiver username
     * @return CompletableFuture that completes when connection is established
     */
    CompletableFuture<TransferSession> connectToTransfer(String transferCode, String username);
    
    /**
     * Start file transfer after receiver approval
     * @param transferCode transfer code for the session
     * @param progressCallback callback for transfer progress updates
     * @param completionCallback callback for transfer completion
     * @return CompletableFuture that completes when transfer is finished
     */
    CompletableFuture<Void> startFileTransfer(String transferCode, 
                                            Consumer<TransferProgress> progressCallback,
                                            Consumer<TransferComplete> completionCallback);
    
    /**
     * Save received file to disk
     * @param transferCode transfer code for the session
     * @param fileName name of the file to save
     * @param fileData encrypted file data
     * @param targetDirectory directory to save the file
     * @return CompletableFuture that completes when file is saved
     */
    CompletableFuture<File> saveReceivedFile(String transferCode, String fileName, byte[] fileData, File targetDirectory);
    
    /**
     * Get sender transfer history
     * @param sessionId session ID to filter by (optional)
     * @return list of sender transfers
     */
    List<SenderTransfer> getSenderTransferHistory(String sessionId);
    
    /**
     * Get receiver transfer history
     * @param sessionId session ID to filter by (optional)
     * @return list of receiver transfers
     */
    List<ReceiverTransfer> getReceiverTransferHistory(String sessionId);
    
    /**
     * Cancel an active transfer
     * @param transferCode transfer code to cancel
     */
    void cancelTransfer(String transferCode);
    
    /**
     * Check if a transfer is active
     * @param transferCode transfer code to check
     * @return true if transfer is active
     */
    boolean isTransferActive(String transferCode);
    
    /**
     * Get active transfer sessions
     * @return list of active transfer sessions
     */
    List<TransferSession> getActiveSessions();
    
    /**
     * Clean up completed transfers
     */
    void cleanupCompletedTransfers();
    
    /**
     * Transfer progress information
     */
    class TransferProgress {
        private final String transferCode;
        private final String fileName;
        private final double progress;
        private final long bytesTransferred;
        private final long totalBytes;
        
        public TransferProgress(String transferCode, String fileName, double progress, long bytesTransferred, long totalBytes) {
            this.transferCode = transferCode;
            this.fileName = fileName;
            this.progress = progress;
            this.bytesTransferred = bytesTransferred;
            this.totalBytes = totalBytes;
        }
        
        public String getTransferCode() { return transferCode; }
        public String getFileName() { return fileName; }
        public double getProgress() { return progress; }
        public long getBytesTransferred() { return bytesTransferred; }
        public long getTotalBytes() { return totalBytes; }
    }
    
    /**
     * Transfer completion information
     */
    class TransferComplete {
        private final String transferCode;
        private final String fileName;
        private final boolean success;
        private final String errorMessage;
        private final String checksum;
        
        public TransferComplete(String transferCode, String fileName, boolean success, String errorMessage, String checksum) {
            this.transferCode = transferCode;
            this.fileName = fileName;
            this.success = success;
            this.errorMessage = errorMessage;
            this.checksum = checksum;
        }
        
        public String getTransferCode() { return transferCode; }
        public String getFileName() { return fileName; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getChecksum() { return checksum; }
    }
} 