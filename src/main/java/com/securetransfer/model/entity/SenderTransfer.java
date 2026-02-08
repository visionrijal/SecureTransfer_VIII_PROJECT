package com.securetransfer.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity for tracking sender transfer history.
 * Stores metadata about sent files without storing the actual files.
 */
@Entity
@Table(name = "sender_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SenderTransfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "receiver_code", nullable = false, length = 6)
    private String receiverCode;
    
    @Column(name = "transfer_status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransferStatus transferStatus;
    
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @Column(name = "session_id", length = 255)
    private String sessionId;
    
    @Column(name = "receiver_username", length = 255)
    private String receiverUsername;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "sender_ip", length = 45)
    private String senderIp;
    
    @Column(name = "sender_port")
    private Integer senderPort;
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getReceiverCode() { return receiverCode; }
    public void setReceiverCode(String receiverCode) { this.receiverCode = receiverCode; }
    public TransferStatus getTransferStatus() { return transferStatus; }
    public void setTransferStatus(TransferStatus transferStatus) { this.transferStatus = transferStatus; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSenderIp() { return senderIp; }
    public void setSenderIp(String senderIp) { this.senderIp = senderIp; }
    public Integer getSenderPort() { return senderPort; }
    public void setSenderPort(Integer senderPort) { this.senderPort = senderPort; }
    public String getFilePath() { return null; } // No file path stored for sender
    
    // Manual builder implementation for environments where Lombok is not working
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private String fileName;
        private Long fileSize;
        private String receiverCode;
        private TransferStatus transferStatus;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String checksum;
        private String sessionId;
        private String receiverUsername;
        private String errorMessage;
        private String senderIp;
        private Integer senderPort;
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder receiverCode(String receiverCode) { this.receiverCode = receiverCode; return this; }
        public Builder transferStatus(TransferStatus transferStatus) { this.transferStatus = transferStatus; return this; }
        public Builder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
        public Builder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
        public Builder checksum(String checksum) { this.checksum = checksum; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder receiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder senderIp(String senderIp) { this.senderIp = senderIp; return this; }
        public Builder senderPort(Integer senderPort) { this.senderPort = senderPort; return this; }
        public SenderTransfer build() {
            SenderTransfer st = new SenderTransfer();
            st.setFileName(fileName);
            st.setFileSize(fileSize);
            st.setReceiverCode(receiverCode);
            st.setTransferStatus(transferStatus);
            st.setStartTime(startTime);
            st.setEndTime(endTime);
            st.setChecksum(checksum);
            st.setSessionId(sessionId);
            st.setReceiverUsername(receiverUsername);
            st.setErrorMessage(errorMessage);
            st.setSenderIp(senderIp);
            st.setSenderPort(senderPort);
            return st;
        }
    }
    
    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (transferStatus == null) {
            transferStatus = TransferStatus.PENDING;
        }
    }
    
    /**
     * Transfer status enumeration
     */
    public enum TransferStatus {
        PENDING,    // Transfer initiated, waiting for receiver
        CONNECTED,  // Receiver connected, ready to transfer
        TRANSFERRING, // File transfer in progress
        COMPLETED,  // Transfer completed successfully
        FAILED,     // Transfer failed
        CANCELLED   // Transfer cancelled by user
    }
} 