
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

    interface FiveMinuteStats {
        Long getTransferCount();
        Long getDistinctRecipients();
        Long getSameRecipientCount();
        Long getSmallTransferCount();
        Long getSmallRecipientCount();
    }

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

    @Query("""
            SELECT
                COUNT(t) AS transferCount,
                COUNT(DISTINCT t.toAccountId) AS distinctRecipients,
                COALESCE(
                    SUM(
                        CASE WHEN t.toAccountId = :receiverAccountId
                        THEN 1 ELSE 0 END
                    ), 0
                ) AS sameRecipientCount,
                COALESCE(
                    SUM(
                        CASE WHEN t.amount <= :smallLimit
                        THEN 1 ELSE 0 END
                    ), 0
                ) AS smallTransferCount,
                COUNT(
                    DISTINCT CASE
                        WHEN t.amount <= :smallLimit
                        THEN t.toAccountId
                        ELSE NULL
                    END
                ) AS smallRecipientCount
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
            """)
    FiveMinuteStats aggregateFiveMinutes(
            @Param("senderAccountId") Long senderAccountId,
            @Param("receiverAccountId") Long receiverAccountId,
            @Param("status") TransferStatus status,
            @Param("smallLimit") BigDecimal smallLimit,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(t)
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
              AND t.toAccountId = :receiverAccountId
            """)
    long countTransfersToRecipient(
            @Param("senderAccountId") Long senderAccountId,
            @Param("receiverAccountId") Long receiverAccountId,
            @Param("status") TransferStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(t)
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.amount <= :smallLimit
              AND t.toAccountId = :receiverAccountId
              AND t.completedAt >= :start
              AND t.completedAt < :end
            """)
    long countSmallTransfersToRecipient(
            @Param("senderAccountId") Long senderAccountId,
            @Param("receiverAccountId") Long receiverAccountId,
            @Param("status") TransferStatus status,
            @Param("smallLimit") BigDecimal smallLimit,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT SUM(t.amount)
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
            """)
    BigDecimal sumOutgoingAmount(
            @Param("senderAccountId") Long senderAccountId,
            @Param("status") TransferStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT DISTINCT t.toAccountId
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
            """)
    List<Long> findDistinctOutgoingRecipientIds(
            @Param("senderAccountId") Long senderAccountId,
            @Param("status") TransferStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT DISTINCT t.toAccountId
            FROM FdsTransferRead t
            WHERE t.fromAccountId = :senderAccountId
              AND t.status = :status
              AND t.completedAt >= :start
              AND t.completedAt < :end
              AND t.toAccountId IN :recipientIds
            """)
    List<Long> findPreviousRecipientIdsAmong(
            @Param("senderAccountId") Long senderAccountId,
            @Param("status") TransferStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("recipientIds") List<Long> recipientIds
    );

    @Query("""
            SELECT t
            FROM FdsTransferRead t
            WHERE t.toAccountId = :toAccountId
              AND t.status = :status
              AND t.amount >= :minimumAmount
              AND t.completedAt >= :start
              AND t.completedAt < :end
            ORDER BY t.completedAt DESC
            """)
    List<FdsTransferRead> findLargeDeposits(
            @Param("toAccountId") Long toAccountId,
            @Param("status") TransferStatus status,
            @Param("minimumAmount") BigDecimal minimumAmount,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            org.springframework.data.domain.Pageable pageable
    );
}
