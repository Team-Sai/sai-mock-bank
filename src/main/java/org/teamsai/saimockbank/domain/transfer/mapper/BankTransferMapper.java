
package org.teamsai.saimockbank.domain.transfer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BankTransferMapper {

    Optional<BankTransferDTO> findById(
            @Param("transferId") Long transferId
    );

    Optional<BankTransferDTO> findByRequestKey(
            @Param("requestKey") String requestKey
    );

    int insert(BankTransferDTO transfer);

    @Select("""
            SELECT *
            FROM bank_transfer
            WHERE transfer_id = #{transferId}
            FOR UPDATE
            """)
    Optional<BankTransferDTO> findByIdForUpdate(
            @Param("transferId") Long transferId
    );

    @Select("""
            SELECT *
            FROM bank_transfer
            WHERE status = #{status}
            ORDER BY created_at DESC, transfer_id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<BankTransferDTO> findReviews(
            @Param("status") TransferStatus status,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            SELECT COUNT(*)
            FROM bank_transfer
            WHERE status = #{status}
            """)
    long countReviews(
            @Param("status") TransferStatus status
    );

    @Update("""
            UPDATE bank_transfer
            SET status = 'SUCCESS',
                completed_at = #{completedAt},
                reviewed_by = #{adminId},
                reviewed_at = #{reviewedAt},
                review_reason = #{reason},
                failure_reason = NULL
            WHERE transfer_id = #{transferId}
              AND status = 'PENDING_REVIEW'
            """)
    int approvePending(
            @Param("transferId") Long transferId,
            @Param("adminId") Long adminId,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("reason") String reason
    );

    @Update("""
            UPDATE bank_transfer
            SET status = 'REJECTED',
                reviewed_by = #{adminId},
                reviewed_at = #{reviewedAt},
                review_reason = #{reason},
                failure_reason = '관리자 심사에서 거절된 거래입니다.'
            WHERE transfer_id = #{transferId}
              AND status = 'PENDING_REVIEW'
            """)
    int rejectPending(
            @Param("transferId") Long transferId,
            @Param("adminId") Long adminId,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("reason") String reason
    );
}
