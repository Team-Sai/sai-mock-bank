
package org.teamsai.saimockbank.domain.fds.dto.response;

import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FdsReviewResponse(
        Long transferId,
        String requestKey,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransferStatus status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        Integer riskScore,
        FdsRiskLevel riskLevel,
        List<FdsRuleType> matchedRules,
        List<String> reasons
) {

    public static FdsReviewResponse from(
            BankTransferDTO transfer,
            FdsEvaluationResponse evaluation
    ) {
        return new FdsReviewResponse(
                transfer.getTransferId(),
                transfer.getRequestKey(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getReviewedBy(),
                transfer.getReviewedAt(),
                transfer.getReviewReason(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt(),
                evaluation == null ? null : evaluation.riskScore(),
                evaluation == null ? null : evaluation.riskLevel(),
                evaluation == null ? List.of() : evaluation.matchedRules(),
                evaluation == null ? List.of() : evaluation.reasons()
        );
    }

    public static FdsReviewResponse from(
            BankTransferDTO transfer
    ) {
        return from(transfer, null);
    }
}
