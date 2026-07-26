package org.teamsai.saimockbank.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.entity.BankAccount;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionResponse;
import org.teamsai.saimockbank.domain.transaction.exception.TransactionErrorCode;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionService {

    private final BankAccountMapper bankAccountMapper;
    private final BankTransactionMapper bankTransactionMapper;

    public List<TransactionResponse> getTransactions(
            Long accountId,
            String userKey,
            Long afterTransactionId
    ) {
        validateRequest(accountId, userKey);

        BankAccount account = bankAccountMapper.findById(accountId)
                .orElseThrow(
                        TransactionErrorCode.ACCOUNT_NOT_FOUND
                                ::toException
                );

        if (!account.getUserKey().equals(userKey)) {
            throw TransactionErrorCode.ACCOUNT_ACCESS_DENIED
                    .toException();
        }

        long cursor =
                afterTransactionId == null
                        ? 0L
                        : afterTransactionId;

        return bankTransactionMapper
                .findAllByAccountIdAfter(accountId, cursor)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private void validateRequest(
            Long accountId,
            String userKey
    ) {
        if (accountId == null
                || accountId <= 0
                || userKey == null
                || userKey.isBlank()) {

            throw TransactionErrorCode
                    .INVALID_TRANSACTION_REQUEST
                    .toException();
        }
    }
}
