package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BankTransferService {

    private final BankTransferMapper bankTransferMapper;
    private final BankAccountMapper bankAccountMapper;
    private final TransferProcessor transferProcessor;

    @Transactional
    public TransferResponse transfer(Long loginUserId, TransferRequest request) {
        validateRequest(request);

        // 계좌번호 -> accountId 변환은 여기서 한 번만. 이후 단계는 전부 accountId 기준으로만 다룸.
        Long toAccountId = resolveToAccountId(request.toAccountNumber());

        BankTransferDTO existTransfer = bankTransferMapper.findByRequestKey(request.requestKey()).orElse(null);
        if (existTransfer != null) {
            validateDuplicateRequest(existTransfer, request, toAccountId);
            return toResponse(existTransfer);
        }

        BankTransferDTO transfer = transferProcessor.process(request, loginUserId, toAccountId);

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long loginUserId, Long transferId) {
        if (transferId == null || transferId <= 0) {
            throw TransferErrorCode.INVALID_TRANSFER_REQUEST.toException();
        }

        BankTransferDTO transfer = bankTransferMapper.findById(transferId)
                .orElseThrow(TransferErrorCode.TRANSFER_NOT_FOUND::toException);

        validateParticipant(loginUserId, transfer);

        return toResponse(transfer);
    }

    // 이체 당사자(출금/입금 어느 쪽이든) 본인 확인. 이체 결과 조회는 남의 것을 볼 수 없어야 함.
    private void validateParticipant(Long loginUserId, BankTransferDTO transfer) {
        boolean isParticipant =
                bankAccountMapper.findById(transfer.getFromAccountId())
                        .map(account -> account.getBankUserId().equals(loginUserId))
                        .orElse(false)
                        || bankAccountMapper.findById(transfer.getToAccountId())
                        .map(account -> account.getBankUserId().equals(loginUserId))
                        .orElse(false);

        if (!isParticipant) {
            throw TransferErrorCode.ACCOUNT_ACCESS_DENIED.toException();
        }
    }

    // 응답에 마스킹된 계좌번호를 채우기 위해 두 계좌를 조회해서 팩토리로 넘김.
    private TransferResponse toResponse(BankTransferDTO transfer) {
        BankAccountDTO fromAccount = bankAccountMapper.findById(transfer.getFromAccountId()).orElse(null);
        BankAccountDTO toAccount = bankAccountMapper.findById(transfer.getToAccountId()).orElse(null);

        if (fromAccount == null || toAccount == null) {
            return TransferResponse.from(transfer);
        }
        return TransferResponse.from(transfer, fromAccount, toAccount);
    }

    private Long resolveToAccountId(String toAccountNumber) {
        BankAccountDTO toAccount = bankAccountMapper.findByAccountNumber(toAccountNumber)
                .orElseThrow(TransferErrorCode.ACCOUNT_NOT_FOUND::toException);
        return toAccount.getAccountId();
    }

    private void validateRequest(TransferRequest request) {
        if (request == null
                || isBlank(request.requestKey())
                || request.fromAccountId() == null
                || request.fromAccountId() <= 0
                || isBlank(request.toAccountNumber())
                || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw TransferErrorCode.INVALID_TRANSFER_REQUEST
                    .toException();
        }
    }

    private void validateDuplicateRequest(
            BankTransferDTO existingTransfer,
            TransferRequest request,
            Long toAccountId
    ) {
        boolean sameTransfer =
                existingTransfer.getFromAccountId().equals(request.fromAccountId())
                        && existingTransfer.getToAccountId().equals(toAccountId)
                        && existingTransfer.getAmount().compareTo(request.amount()) == 0;

        if (!sameTransfer) {
            throw TransferErrorCode.DUPLICATE_REQUEST_KEY
                    .toException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}