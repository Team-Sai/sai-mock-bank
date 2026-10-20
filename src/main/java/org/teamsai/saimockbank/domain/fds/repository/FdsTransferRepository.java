package org.teamsai.saimockbank.domain.fds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.teamsai.saimockbank.domain.fds.entity.FdsTransferRead;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FdsTransferRepository
        extends JpaRepository<FdsTransferRead, Long> {


    List<FdsTransferRead>
    findByFromAccountIdAndStatusAndCompletedAtBetween(
            Long fromAccountId,
            TransferStatus status,
            LocalDateTime start,
            LocalDateTime end
    );


    Optional<FdsTransferRead>
    findFirstByToAccountIdAndStatusAndAmountGreaterThanEqualAndCompletedAtBetweenOrderByCompletedAtDesc(
            Long toAccountId,
            TransferStatus status,
            BigDecimal minimumAmount,
            LocalDateTime start,
            LocalDateTime end
    );


    List<FdsTransferRead>
    findByFromAccountIdAndStatusAndCompletedAtBetweenOrderByCompletedAtAsc(
            Long fromAccountId,
            TransferStatus status,
            LocalDateTime start,
            LocalDateTime end
    );


    long countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long fromAccountId,
            TransferStatus status,
            LocalDateTime start,
            LocalDateTime end
    );


    @Query("""
            SELECT DISTINCT t.toAccountId
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
            """)
    List<Long> findPreviousRecipientIds(
            @Param("senderAccountId") Long senderAccountId,
            @Param("status") TransferStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}