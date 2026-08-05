package org.teamsai.saimockbank.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.AccountDetailResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountService {

    private final BankAccountMapper bankAccountMapper;
    private final UserKeyHasher userKeyHasher;
    private final AccountOwnershipValidator accountOwnershipValidator;

    public List<AccountListResponse> getMyAccounts(Long bankUserId) {
        return bankAccountMapper.findAllByBankUserId(bankUserId)
                .stream()
                .map(AccountListResponse::from)
                .toList();
    }

    public List<AccountListResponse> getAccounts(String userKey) {
        validateUserKey(userKey);

        String hashedKey = userKeyHasher.hash(userKey);

        return bankAccountMapper.findAllByUserKey(hashedKey)
                .stream()
                .map(AccountListResponse::from)
                .toList();
    }

    public AccountDetailResponse getAccount(
            Long accountId,
            String userKey
    ) {
        validateRequest(accountId, userKey);

        BankAccountDTO account = bankAccountMapper.findById(accountId)
                .orElseThrow(AccountErrorCode.ACCOUNT_NOT_FOUND::toException);

        String ownerHash = bankAccountMapper.findOwnerUserKeyHashByAccountId(accountId)
                .orElse(null);

        accountOwnershipValidator.verify(
                ownerHash,
                userKey,
                AccountErrorCode.ACCOUNT_ACCESS_DENIED::toException
        );

        return AccountDetailResponse.from(account);
    }

    private void validateUserKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST.toException();
        }
    }

    private void validateRequest(
            Long accountId,
            String userKey
    ) {
        if (accountId == null
                || accountId <= 0
                || userKey == null
                || userKey.isBlank()) {
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST.toException();
        }
    }
}