package com.securetransfer.repository;

import com.securetransfer.model.entity.SenderTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SenderTransferRepository extends JpaRepository<SenderTransfer, Long> {
    
    /**
     * Find transfers by receiver code
     */
    List<SenderTransfer> findByReceiverCodeOrderByStartTimeDesc(String receiverCode);
    
    /**
     * Find transfers by session ID
     */
    List<SenderTransfer> findBySessionIdOrderByStartTimeDesc(String sessionId);
    
    /**
     * Find transfers by status
     */
    List<SenderTransfer> findByTransferStatusOrderByStartTimeDesc(SenderTransfer.TransferStatus status);
    
    /**
     * Find transfers within a date range
     */
    @Query("SELECT st FROM SenderTransfer st WHERE st.startTime BETWEEN :startDate AND :endDate ORDER BY st.startTime DESC")
    List<SenderTransfer> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find recent transfers (last 30 days)
     */
    @Query("SELECT st FROM SenderTransfer st WHERE st.startTime >= :thirtyDaysAgo ORDER BY st.startTime DESC")
    List<SenderTransfer> findRecentTransfers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);
    
    /**
     * Find transfers by file name (case-insensitive)
     */
    @Query("SELECT st FROM SenderTransfer st WHERE LOWER(st.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')) ORDER BY st.startTime DESC")
    List<SenderTransfer> findByFileNameContainingIgnoreCase(@Param("fileName") String fileName);
    
    /**
     * Count transfers by status
     */
    long countByTransferStatus(SenderTransfer.TransferStatus status);
    
    /**
     * Find failed transfers
     */
    List<SenderTransfer> findByTransferStatusInOrderByStartTimeDesc(List<SenderTransfer.TransferStatus> statuses);
    
    /**
     * Delete old transfers (older than specified date)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SenderTransfer st WHERE st.startTime < :cutoffDate")
    int deleteOldTransfers(@Param("cutoffDate") LocalDateTime cutoffDate);
} 