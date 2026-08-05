package org.teamsai.saimockbank.domain.transaction.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        String transactionKey,
        Long accountId,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String counterpartyName,
        String maskedCounterpartyAccountNumber,
        String memo,
        LocalDateTime transactionAt,
        Long transferId
) {

    public static TransactionResponse from(
            BankTransactionDTO transaction
    ) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getTransactionKey(),
                transaction.getAccountId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyName(),
                AccountNumberMasker.mask(
                        transaction.getCounterpartyAccountNumber()
                ),
                transaction.getMemo(),
                transaction.getTransactionAt(),
                transaction.getTransferId()
        );
    }
}