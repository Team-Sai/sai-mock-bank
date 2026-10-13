package org.teamsai.saimockbank.domain.transfer.dto;

import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transferId,
        String requestKey,
        Long fromAccountId,
        String fromMaskedAccountNumber,
        Long toAccountId,
        String toMaskedAccountNumber,
        BigDecimal amount,
        TransferStatus status,
        String failureReason,
        LocalDateTime completedAt
) {
    public static TransferResponse from(
            BankTransferDTO transfer,
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount
    ) {
        return new TransferResponse(
                transfer.getTransferId(),
                transfer.getRequestKey(),
                transfer.getFromAccountId(),
                AccountNumberMasker.mask(fromAccount.getAccountNumber()),
                transfer.getToAccountId(),
                AccountNumberMasker.mask(toAccount.getAccountNumber()),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCompletedAt()
        );
    }

    public static TransferResponse from(BankTransferDTO transfer) {
        return new TransferResponse(
                transfer.getTransferId(),
                transfer.getRequestKey(),
                transfer.getFromAccountId(),
                null,
                transfer.getToAccountId(),
                null,
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCompletedAt()
        );
    }
}