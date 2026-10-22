package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.fds.repository.FdsEvaluationRepository;
import org.teamsai.saimockbank.domain.fds.service.FdsAdminReviewService;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.TransferProcessor;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsAdminReviewServiceTest {

    @Mock
    private BankTransferMapper mapper;

    @Mock
    private TransferProcessor processor;

    @Mock
    private FdsEvaluationRepository evaluationRepository;

    @InjectMocks
    private FdsAdminReviewService service;

    private BankTransferDTO transfer(TransferStatus status) {
        return BankTransferDTO.builder()
                .requestKey("T1")
                .fromAccountId(20L)
                .toAccountId(21L)
                .amount(BigDecimal.valueOf(10_000))
                .status(status)
                .build();
    }

    @Test
    void 보류건을_거절하면_한번만_상태를_변경한다() {
        BankTransferDTO pending = transfer(TransferStatus.PENDING_REVIEW);

        when(mapper.findByIdForUpdate(8L))
                .thenReturn(Optional.of(pending));
        when(mapper.rejectPending(eq(8L), eq(1L), any(), eq("의심 거래")))
                .thenReturn(1);
        when(mapper.findById(8L))
                .thenReturn(Optional.of(transfer(TransferStatus.REJECTED)));

        assertThat(service.reject(8L, 1L, "의심 거래").status())
                .isEqualTo(TransferStatus.REJECTED);

        verify(mapper, times(1))
                .rejectPending(eq(8L), eq(1L), any(), eq("의심 거래"));
        verifyNoInteractions(processor);
    }

    @Test
    void 이미_심사가_끝난_거래는_거부한다() {
        when(mapper.findByIdForUpdate(8L))
                .thenReturn(Optional.of(transfer(TransferStatus.SUCCESS)));

        assertThatThrownBy(() -> service.approve(8L, 1L, null))
                .isInstanceOf(DomainException.class);

        verifyNoInteractions(processor);
        verifyNoInteractions(evaluationRepository);
    }

    @Test
    void 보류건_승인은_기존_이체_처리기로_위임한다() {
        BankTransferDTO pending = transfer(TransferStatus.PENDING_REVIEW);

        when(mapper.findByIdForUpdate(8L))
                .thenReturn(Optional.of(pending));
        when(processor.processApproved(pending, 1L, "확인"))
                .thenReturn(transfer(TransferStatus.SUCCESS));

        assertThat(service.approve(8L, 1L, "확인").status())
                .isEqualTo(TransferStatus.SUCCESS);

        verify(processor).processApproved(pending, 1L, "확인");
    }
}
