
package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BankTransferService {

    private final BankTransferMapper bankTransferMapper;
    private final BankAccountMapper bankAccountMapper;
    private final FdsTransferCommandService commandService;
    private final FdsPreTransferDecisionService fdsDecisionService;

    public TransferResponse transfer(
            Long loginUserId,
            TransferRequest request
    ) {
        validateRequest(request);

        Long toAccountId =
                resolveToAccountId(request.toAccountNumber());

        BankTransferDTO existing =
                bankTransferMapper.findByRequestKey(
                        request.requestKey()
                ).orElse(null);

        if (existing != null) {
            return resolveExisting(
                    loginUserId,
                    request,
                    toAccountId,
                    existing
            );
        }

        FdsPreTransferDecisionService.Decision decision =
                fdsDecisionService.assess(
                        request.fromAccountId(),
                        toAccountId,
                        request.amount()
                );

        try {
            BankTransferDTO transfer =
                    commandService.execute(
                            loginUserId,
                            request,
                            toAccountId,
                            decision
                    );

            return toResponse(transfer);

        } catch (DuplicateKeyException exception) {
            BankTransferDTO duplicated =
                    bankTransferMapper.findByRequestKey(
                            request.requestKey()
                    ).orElse(null);

            if (duplicated == null) {
                throw exception;
            }

            return resolveExisting(
                    loginUserId,
                    request,
                    toAccountId,
                    duplicated
            );
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(
            Long loginUserId,
            Long transferId
    ) {
        if (transferId == null || transferId <= 0) {
            throw TransferErrorCode.INVALID_TRANSFER_REQUEST
                    .toException();
        }

        BankTransferDTO transfer =
                bankTransferMapper.findById(transferId)
                        .orElseThrow(
                                TransferErrorCode.TRANSFER_NOT_FOUND::toException
                        );

        validateParticipant(loginUserId, transfer);

        return toResponse(transfer);
    }

    private TransferResponse resolveExisting(
            Long loginUserId,
            TransferRequest request,
            Long toAccountId,
            BankTransferDTO existing
    ) {
        validateSender(loginUserId, existing);

        validateDuplicateRequest(
                existing,
                request,
                toAccountId
        );

        return toResponse(existing);
    }

    private void validateSender(
            Long loginUserId,
            BankTransferDTO transfer
    ) {
        boolean owner =
                bankAccountMapper.findById(
                        transfer.getFromAccountId()
                ).map(account ->
                        account.getBankUserId().equals(loginUserId)
                ).orElse(false);

        if (!owner) {
            throw TransferErrorCode.ACCOUNT_ACCESS_DENIED
                    .toException();
        }
    }

    private void validateParticipant(
            Long loginUserId,
            BankTransferDTO transfer
    ) {
        boolean isParticipant =
                bankAccountMapper.findById(
                        transfer.getFromAccountId()
                ).map(account ->
                        account.getBankUserId().equals(loginUserId)
                ).orElse(false)

                        || bankAccountMapper.findById(
                        transfer.getToAccountId()
                ).map(account ->
                        account.getBankUserId().equals(loginUserId)
                ).orElse(false);

        if (!isParticipant) {
            throw TransferErrorCode.ACCOUNT_ACCESS_DENIED
                    .toException();
        }
    }

    private TransferResponse toResponse(
            BankTransferDTO transfer
    ) {
        BankAccountDTO fromAccount =
                bankAccountMapper.findById(
                        transfer.getFromAccountId()
                ).orElse(null);

        BankAccountDTO toAccount =
                bankAccountMapper.findById(
                        transfer.getToAccountId()
                ).orElse(null);

        if (fromAccount == null || toAccount == null) {
            return TransferResponse.from(transfer);
        }

        return TransferResponse.from(
                transfer,
                fromAccount,
                toAccount
        );
    }

    private Long resolveToAccountId(
            String toAccountNumber
    ) {
        BankAccountDTO toAccount =
                bankAccountMapper.findByAccountNumber(
                        toAccountNumber
                ).orElseThrow(
                        TransferErrorCode.ACCOUNT_NOT_FOUND::toException
                );

        return toAccount.getAccountId();
    }

    private void validateRequest(
            TransferRequest request
    ) {
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
            BankTransferDTO existing,
            TransferRequest request,
            Long toAccountId
    ) {
        boolean sameTransfer =
                existing.getFromAccountId()
                        .equals(request.fromAccountId())
                        && existing.getToAccountId().equals(toAccountId)
                        && existing.getAmount()
                        .compareTo(request.amount()) == 0;

        if (!sameTransfer) {
            throw TransferErrorCode.DUPLICATE_REQUEST_KEY
                    .toException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
