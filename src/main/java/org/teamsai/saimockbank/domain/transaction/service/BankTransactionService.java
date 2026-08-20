package org.teamsai.saimockbank.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionResponse;
import org.teamsai.saimockbank.domain.transaction.exception.TransactionErrorCode;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final BankAccountMapper bankAccountMapper;
    private final BankTransactionMapper bankTransactionMapper;
    private final AccountOwnershipValidator ownershipValidator;

    public List<TransactionResponse> getTransactions(
            Long accountId,
            String userKey,
            Long afterTransactionId
    ) {
        validateRequest(accountId, userKey);

        bankAccountMapper.findById(accountId)
                .orElseThrow(
                        TransactionErrorCode.ACCOUNT_NOT_FOUND
                                ::toException
                );

        String ownerHash = bankAccountMapper.findOwnerUserKeyHashByAccountId(accountId)
                .orElse(null);

        ownershipValidator.verify(
                ownerHash,
                userKey,
                TransactionErrorCode.ACCOUNT_ACCESS_DENIED::toException
        );

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

    public List<TransactionResponse> getMyTransactions(
            Long bankUserId,
            Long accountId,
            Long beforeTransactionId,
            int size
    ) {
        validateRequest(accountId, bankUserId);
        int safeSize = clampSize(size);

        boolean owned = bankAccountMapper.findAllByBankUserId(bankUserId).stream()
                .anyMatch(account -> account.getAccountId().equals(accountId));

        if (!owned) {
            throw TransactionErrorCode.ACCOUNT_ACCESS_DENIED.toException();
        }

        return bankTransactionMapper
                .findRecentByAccountId(accountId, beforeTransactionId, safeSize)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private void validateRequest(Long accountId, Long bankUserId) {
        if (accountId == null || accountId <= 0 || bankUserId == null) {
            throw TransactionErrorCode.INVALID_TRANSACTION_REQUEST.toException();
        }
    }

    public List<TransactionResponse> getMyAllTransactions(
            Long bankUserId,
            Long beforeTransactionId,
            int size
    ) {
        int safeSize = clampSize(size);

        List<Long> accountIds = bankAccountMapper.findAllByBankUserId(bankUserId).stream()
                .map(BankAccountDTO::getAccountId)
                .toList();

        if (accountIds.isEmpty()) {
            return List.of();
        }

        return bankTransactionMapper
                .findRecentByAccountIds(accountIds, beforeTransactionId, safeSize)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private int clampSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            return MIN_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return size;
    }
}
