package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsReviewResponse;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.exception.TransferReviewErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.TransferProcessor;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FdsAdminReviewService {

    private final BankTransferMapper transferMapper;
    private final TransferProcessor transferProcessor;

    @Transactional(readOnly = true)
    public Page<FdsReviewResponse> pending(
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw TransferReviewErrorCode.INVALID_PAGINATION
                    .toException();
        }

        PageRequest pageable = PageRequest.of(page, size);

        return new PageImpl<>(
                transferMapper.findReviews(
                                TransferStatus.PENDING_REVIEW,
                                (int) pageable.getOffset(),
                                size
                        ).stream()
                        .map(FdsReviewResponse::from)
                        .toList(),
                pageable,
                transferMapper.countReviews(
                        TransferStatus.PENDING_REVIEW
                )
        );
    }

    @Transactional(readOnly = true)
    public FdsReviewResponse detail(Long transferId) {
        BankTransferDTO transfer =
                transferMapper.findById(transferId)
                        .orElseThrow(
                                TransferErrorCode.TRANSFER_NOT_FOUND::toException
                        );

        return FdsReviewResponse.from(transfer);
    }

    @Transactional
    public FdsReviewResponse approve(
            Long transferId,
            Long adminId,
            String reason
    ) {
        BankTransferDTO transfer =
                pendingForUpdate(transferId);

        BankTransferDTO completed =
                transferProcessor.processApproved(
                        transfer,
                        adminId,
                        reason
                );

        return FdsReviewResponse.from(completed);
    }

    @Transactional
    public FdsReviewResponse reject(
            Long transferId,
            Long adminId,
            String reason
    ) {
        if (reason == null || reason.isBlank()
                || reason.length() > 255) {
            throw TransferReviewErrorCode.INVALID_REVIEW_REASON
                    .toException();
        }

        pendingForUpdate(transferId);

        int updated = transferMapper.rejectPending(
                transferId,
                adminId,
                LocalDateTime.now(),
                reason
        );

        if (updated != 1) {
            throw TransferReviewErrorCode.REVIEW_NOT_PENDING
                    .toException();
        }

        return detail(transferId);
    }

    private BankTransferDTO pendingForUpdate(
            Long transferId
    ) {
        BankTransferDTO transfer =
                transferMapper.findByIdForUpdate(transferId)
                        .orElseThrow(
                                TransferErrorCode.TRANSFER_NOT_FOUND::toException
                        );

        if (transfer.getStatus() != TransferStatus.PENDING_REVIEW) {
            throw TransferReviewErrorCode.REVIEW_NOT_PENDING
                    .toException();
        }

        return transfer;
    }
}
