package org.teamsai.saimockbank.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.AccountDetailResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.entity.BankAccount;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountService {

    private final BankAccountMapper bankAccountMapper;

    public List<AccountListResponse> getAccounts(String userKey) {
        validateUserKey(userKey);

        return bankAccountMapper.findAllByUserKey(userKey)
                .stream()
                .map(AccountListResponse::from)
                .toList();
    }

    public AccountDetailResponse getAccount(
            Long accountId,
            String userKey
    ) {
        validateRequest(accountId, userKey);

        BankAccount account = bankAccountMapper.findById(accountId)
                .orElseThrow(
                        AccountErrorCode.ACCOUNT_NOT_FOUND::toException
                );

        if (!account.getUserKey().equals(userKey)) {
            throw AccountErrorCode.ACCOUNT_ACCESS_DENIED
                    .toException();
        }

        return AccountDetailResponse.from(account);
    }

    private void validateUserKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST
                    .toException();
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
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST
                    .toException();
        }
    }
}