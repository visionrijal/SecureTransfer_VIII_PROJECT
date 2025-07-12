package com.securetransfer.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity for tracking receiver transfer history.
 * Stores metadata about received files and their save status.
 */
@Entity
@Table(name = "receiver_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiverTransfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "sender_code", nullable = false, length = 6)
    private String senderCode;
    
    @Column(name = "transfer_status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransferStatus transferStatus;
    
    @Column(name = "received_time", nullable = false)
    private LocalDateTime receivedTime;
    
    @Column(name = "saved_time")
    private LocalDateTime savedTime;
    
    @Column(name = "file_path", length = 500)
    private String filePath;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @Column(name = "session_id", length = 255)
    private String sessionId;
    
    @Column(name = "sender_username", length = 255)
    private String senderUsername;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "auto_saved", nullable = false)
    @Builder.Default
    private Boolean autoSaved = false;
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSenderCode() { return senderCode; }
    public void setSenderCode(String senderCode) { this.senderCode = senderCode; }
    public TransferStatus getTransferStatus() { return transferStatus; }
    public void setTransferStatus(TransferStatus transferStatus) { this.transferStatus = transferStatus; }
    public LocalDateTime getReceivedTime() { return receivedTime; }
    public void setReceivedTime(LocalDateTime receivedTime) { this.receivedTime = receivedTime; }
    public LocalDateTime getSavedTime() { return savedTime; }
    public void setSavedTime(LocalDateTime savedTime) { this.savedTime = savedTime; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Boolean getAutoSaved() { return autoSaved; }
    public void setAutoSaved(Boolean autoSaved) { this.autoSaved = autoSaved; }
    public byte[] getFileData() { return null; } // No file data stored in entity
    
    @PrePersist
    protected void onCreate() {
        if (receivedTime == null) {
            receivedTime = LocalDateTime.now();
        }
        if (transferStatus == null) {
            transferStatus = TransferStatus.RECEIVING;
        }
        if (autoSaved == null) {
            autoSaved = false;
        }
    }
    
    /**
     * Transfer status enumeration
     */
    public enum TransferStatus {
        RECEIVING,  // File being received
        RECEIVED,   // File received, waiting to be saved
        SAVED,      // File saved to disk
        FAILED,     // Transfer or save failed
        CANCELLED   // Transfer cancelled
    }
} 