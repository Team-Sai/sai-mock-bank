package org.teamsai.saimockbank.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.*;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.exception.UserErrorCode;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import java.util.UUID;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountService {

    private final BankAccountMapper bankAccountMapper;
    private final UserMapper userMapper;
    private final UserKeyHasher userKeyHasher;
    private final AccountOwnershipValidator accountOwnershipValidator;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 3;

    @Transactional
    public AccountDetailResponse postAccount(
            Long bankUserId,
            UUID idempotencyKey,
            CreateAccountRequest request
    ) {
        if (bankUserId == null || bankUserId <= 0 || idempotencyKey == null) {
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST.toException();
        }

        CreateAccountRequest conditions = (request == null ? CreateAccountRequest.defaults() : request).normalized();
        String requestHash = conditions.fingerprint();

        // Serialize creation per owner, including concurrent retries on different app instances.
        var user = userMapper.findByIdForUpdate(bankUserId)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        String requestId = idempotencyKey.toString();
        var previous = bankAccountMapper.findByCreationRequestForUpdate(bankUserId, requestId);
        if (previous.isPresent()) {
            return replayCreation(previous.get(), requestHash);
        }

        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            BankAccountDTO account = BankAccountDTO.builder()
                    .bankUserId(user.getBankUserId())
                    .creationRequestId(requestId)
                    .creationRequestHash(requestHash)
                    .bankCode(conditions.bankCode())
                    .bankName(conditions.bankName())
                    .accountNumber(generateAccountNumber(conditions.bankCode()))
                    .accountName(conditions.accountName())
                    .accountHolderName(user.getName())
                    .balance(conditions.initialBalance())
                    .status(AccountStatus.ACTIVE)
                    .build();

            try {
                if (bankAccountMapper.insert(account) != 1) {
                    throw new IllegalStateException("계좌 생성에 실패했습니다.");
                }
            } catch (DuplicateKeyException e) {
                // 같은 사용자 + 요청 키로 이미 생성됐다면 해당 계좌를 반환합니다.
                var existing = bankAccountMapper.findByCreationRequestForUpdate(
                        bankUserId,
                        requestId
                );

                if (existing.isPresent()) {
                    return replayCreation(existing.get(), requestHash);
                }

                // 같은 요청이 없다면 계좌번호 충돌이므로 번호만 다시 생성합니다.
                if (attempt == MAX_ACCOUNT_NUMBER_ATTEMPTS - 1) {
                    throw e;
                }
                continue;
            }

            BankAccountDTO saved = bankAccountMapper.findById(account.getAccountId())
                    .orElseThrow(() ->
                            new IllegalStateException("생성된 계좌를 조회할 수 없습니다."));

            return AccountDetailResponse.from(saved);
        }

        throw new IllegalStateException("계좌번호 생성에 실패했습니다.");
    }

    private AccountDetailResponse replayCreation(BankAccountDTO existing, String requestHash) {
        if (!requestHash.equals(existing.getCreationRequestHash())) {
            throw AccountErrorCode.ACCOUNT_CREATION_CONFLICT.toException();
        }
        return AccountDetailResponse.from(existing);
    }

    private String generateAccountNumber(String bankCode) {
        // mock-bank용 16자리 번호: 은행 코드 3자리 + 난수 13자리
        StringBuilder number = new StringBuilder("CUSTOM".equals(bankCode) ? "999" : bankCode);

        for (int i = 0; i < 13; i++) {
            number.append(RANDOM.nextInt(10));
        }

        return number.toString();
    }

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


    @Transactional(readOnly = true)
    public BankAccountDTO findByAccountNumber(String accountNumber) {
        return bankAccountMapper.findByAccountNumber(accountNumber)
                .orElseThrow(AccountErrorCode.ACCOUNT_NOT_FOUND::toException);
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