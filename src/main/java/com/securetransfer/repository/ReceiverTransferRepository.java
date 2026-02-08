package com.securetransfer.repository;

import com.securetransfer.model.entity.ReceiverTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReceiverTransferRepository extends JpaRepository<ReceiverTransfer, Long> {
    
    /**
     * Find transfers by sender code
     */
    List<ReceiverTransfer> findBySenderCodeOrderByReceivedTimeDesc(String senderCode);
    
    /**
     * Find transfers by session ID
     */
    List<ReceiverTransfer> findBySessionIdOrderByReceivedTimeDesc(String sessionId);
    
    /**
     * Find transfers by status
     */
    List<ReceiverTransfer> findByTransferStatusOrderByReceivedTimeDesc(ReceiverTransfer.TransferStatus status);
    
    /**
     * Find unsaved transfers (received but not saved)
     */
    List<ReceiverTransfer> findByTransferStatusAndAutoSavedFalseOrderByReceivedTimeDesc(ReceiverTransfer.TransferStatus status);
    
    /**
     * Find transfers within a date range
     */
    @Query("SELECT rt FROM ReceiverTransfer rt WHERE rt.receivedTime BETWEEN :startDate AND :endDate ORDER BY rt.receivedTime DESC")
    List<ReceiverTransfer> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find recent transfers (last 30 days)
     */
    @Query("SELECT rt FROM ReceiverTransfer rt WHERE rt.receivedTime >= :thirtyDaysAgo ORDER BY rt.receivedTime DESC")
    List<ReceiverTransfer> findRecentTransfers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);
    
    /**
     * Find transfers by file name (case-insensitive)
     */
    @Query("SELECT rt FROM ReceiverTransfer rt WHERE LOWER(rt.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')) ORDER BY rt.receivedTime DESC")
    List<ReceiverTransfer> findByFileNameContainingIgnoreCase(@Param("fileName") String fileName);
    
    /**
     * Count transfers by status
     */
    long countByTransferStatus(ReceiverTransfer.TransferStatus status);
    
    /**
     * Find failed transfers
     */
    List<ReceiverTransfer> findByTransferStatusInOrderByReceivedTimeDesc(List<ReceiverTransfer.TransferStatus> statuses);
    
    /**
     * Find transfers that need to be saved
     */
    @Query("SELECT rt FROM ReceiverTransfer rt WHERE rt.transferStatus = 'RECEIVED' AND rt.autoSaved = false ORDER BY rt.receivedTime DESC")
    List<ReceiverTransfer> findPendingSaves();
    
    /**
     * Delete old transfers (older than specified date)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ReceiverTransfer rt WHERE rt.receivedTime < :cutoffDate")
    int deleteOldTransfers(@Param("cutoffDate") LocalDateTime cutoffDate);
} 