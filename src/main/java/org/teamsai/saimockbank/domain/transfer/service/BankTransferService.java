package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final TransferProcessor transferProcessor;

    @Transactional
    public TransferResponse transfer(TransferRequest request){
        validateRequest(request);

        BankTransferDTO existTransfer = bankTransferMapper.findByRequestKey(request.requestKey()).orElse(null);
        if(existTransfer != null){
            validateDuplicateRequest(existTransfer, request);
            return TransferResponse.from(existTransfer);
        }

        BankTransferDTO transfer = transferProcessor.process(request);

        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long transferId){
        if(transferId == null || transferId <=0){
            throw TransferErrorCode.INVALID_TRANSFER_REQUEST.toException();
        }

        BankTransferDTO transfer = bankTransferMapper.findById(transferId)
                .orElseThrow(
                        TransferErrorCode.TRANSFER_NOT_FOUND::toException
                );
        return TransferResponse.from(transfer);
    }

    private void validateRequest(TransferRequest request) {
        if (request == null
                || isBlank(request.requestKey())
                || isBlank(request.fromUserKey())
                || request.fromAccountId() == null
                || request.fromAccountId() <= 0
                || request.toAccountId() == null
                || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw TransferErrorCode.INVALID_TRANSFER_REQUEST
                    .toException();
        }

        if (request.fromAccountId()
                .equals(request.toAccountId())) {

            throw TransferErrorCode.SAME_ACCOUNT_TRANSFER
                    .toException();
        }
    }
    private void validateDuplicateRequest(
            BankTransferDTO existingTransfer,
            TransferRequest request
    ) {
        boolean sameTransfer =
                existingTransfer.getFromAccountId()
                        .equals(request.fromAccountId())
                        && existingTransfer.getToAccountId()
                        .equals(request.toAccountId())
                        && existingTransfer.getAmount()
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
