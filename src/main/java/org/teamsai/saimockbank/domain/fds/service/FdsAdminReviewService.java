package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.fds.dto.FdsReviewNotificationEvent;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsReviewResponse;
import org.teamsai.saimockbank.domain.fds.repository.FdsEvaluationRepository;
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
    private final FdsEvaluationRepository evaluationRepository;

    private final BankAccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;

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
                        )
                        .stream()
                        .map(this::toReviewResponse)
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

        return toReviewResponse(transfer);
    }

    @Transactional
    public FdsReviewResponse approve(
            Long transferId,
            Long adminId,
            String reason
    ) {
        BankTransferDTO transfer =
                pendingForUpdate(transferId);

        // 출금 계좌의 실제 소유자 확인
        Long receiverUserId =
                resolveTransferOwner(transfer.getFromAccountId());

        // 기존 승인 로직 그대로 수행
        BankTransferDTO completed =
                transferProcessor.processApproved(
                        transfer,
                        adminId,
                        reason
                );

        FdsReviewResponse response =
                toReviewResponse(completed);

        // 트랜잭션 커밋 이후에만 SSE 전송됨
        eventPublisher.publishEvent(
                new FdsReviewNotificationEvent(
                        receiverUserId,
                        completed.getTransferId(),
                        completed.getAmount(),
                        "SUCCESS"
                )
        );

        return response;
    }

    @Transactional
    public FdsReviewResponse reject(
            Long transferId,
            Long adminId,
            String reason
    ) {
        if (reason == null
                || reason.isBlank()
                || reason.length() > 255) {

            throw TransferReviewErrorCode.INVALID_REVIEW_REASON
                    .toException();
        }

        BankTransferDTO transfer =
                pendingForUpdate(transferId);

        Long receiverUserId =
                resolveTransferOwner(transfer.getFromAccountId());

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

        FdsReviewResponse response = detail(transferId);

        eventPublisher.publishEvent(
                new FdsReviewNotificationEvent(
                        receiverUserId,
                        transfer.getTransferId(),
                        transfer.getAmount(),
                        "REJECTED"
                )
        );

        return response;
    }

    /**
     * 출금 계좌 소유자에게만 알림을 보낸다.
     * 관리자 또는 수취 계좌 소유자에게는 보내지 않는다.
     */
    private Long resolveTransferOwner(Long accountId) {
        BankAccountDTO account = accountMapper.findById(accountId)
                .orElseThrow(
                        TransferErrorCode.TRANSFER_NOT_FOUND::toException
                );

        if (account.getBankUserId() == null) {
            throw new IllegalStateException(
                    "출금 계좌 소유자를 확인할 수 없습니다."
            );
        }

        return account.getBankUserId();
    }

    private FdsReviewResponse toReviewResponse(
            BankTransferDTO transfer
    ) {
        FdsEvaluationResponse evaluation =
                evaluationRepository
                        .findFirstByTransactionIdOrderByEvaluatedAtDescIdDesc(
                                transfer.getTransferId()
                        )
                        .map(FdsEvaluationResponse::from)
                        .orElse(null);

        return FdsReviewResponse.from(transfer, evaluation);
    }

    private BankTransferDTO pendingForUpdate(Long transferId) {
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