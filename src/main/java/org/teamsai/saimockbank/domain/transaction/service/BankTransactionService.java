package org.teamsai.saimockbank.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.identity.service.UserKeyHasher;
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
    private final UserKeyHasher userKeyHasher;

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
                .orElseThrow(TransactionErrorCode.ACCOUNT_ACCESS_DENIED::toException);

        String hashedKey = userKeyHasher.hash(userKey);
        if (!ownerHash.equals(hashedKey)) {
            throw TransactionErrorCode.ACCOUNT_ACCESS_DENIED.toException();
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
