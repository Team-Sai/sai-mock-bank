package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferHeldEvent;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.FdsTransferCommandService;
import org.teamsai.saimockbank.domain.transfer.service.TransferProcessor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsTransferCommandServiceTest {

    @Mock
    private BankTransferMapper transferMapper;

    @Mock
    private BankAccountMapper accountMapper;

    @Mock
    private TransferProcessor processor;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private FdsTransferCommandService service;

    @Test
    void HIGH는_보류되고_완료이벤트_대신_보류이벤트가_발행된다() {
        TransferRequest request = new TransferRequest(
                "H1",
                20L,
                "12345678",
                BigDecimal.valueOf(10_000),
                null,
                null
        );

        FdsContext context = mock(FdsContext.class);

        FdsPreTransferDecisionService.Decision decision =
                new FdsPreTransferDecisionService.Decision(
                        context,
                        FdsRiskLevel.HIGH
                );

        BankTransferDTO held = BankTransferDTO.builder()
                .requestKey("H1")
                .fromAccountId(20L)
                .toAccountId(21L)
                .amount(BigDecimal.valueOf(10_000))
                .status(TransferStatus.PENDING_REVIEW)
                .build();

        when(transferMapper.findByRequestKey("H1"))
                .thenReturn(Optional.empty());

        when(processor.processHold(request, 1L, 21L))
                .thenReturn(held);

        assertThat(
                service.execute(
                        1L,
                        request,
                        21L,
                        decision
                ).getStatus()
        ).isEqualTo(TransferStatus.PENDING_REVIEW);

        verify(processor, never())
                .process(any(), any(), any());

        verify(publisher)
                .publishEvent(
                        any(FdsTransferHeldEvent.class)
                );
    }
}
