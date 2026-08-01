package org.teamsai.saimockbank.domain.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;
import org.teamsai.saimockbank.domain.transfer.service.TransferProcessor;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransferServiceTest {

    @Mock
    private BankTransferMapper bankTransferMapper;

    @Mock
    private TransferProcessor transferProcessor;

    @InjectMocks
    private BankTransferService bankTransferService;

    @Test
    void 계좌_이체를_실행한다() {
        TransferRequest request = createRequest("TRANSFER-001");

        BankTransferDTO transfer = BankTransferDTO.builder()
                .requestKey("TRANSFER-001")
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(new BigDecimal("10000"))
                .status(TransferStatus.SUCCESS)
                .completedAt(LocalDateTime.now())
                .build();

        when(bankTransferMapper.findByRequestKey("TRANSFER-001"))
                .thenReturn(Optional.empty());

        when(transferProcessor.process(request))
                .thenReturn(transfer);

        var result = bankTransferService.transfer(request);

        assertThat(result.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(result.amount())
                .isEqualByComparingTo("10000");

        verify(transferProcessor).process(request);
    }

    @Test
    void 존재하지_않는_이체는_예외가_발생한다() {
        when(bankTransferMapper.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bankTransferService.getTransfer(999L)
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.TRANSFER_NOT_FOUND.getMessage()
                );
    }

    @Test
    void 동일한_계좌로_이체할_수_없다() {
        TransferRequest request = new TransferRequest(
                "TRANSFER-002",
                "USER_001",
                1L,
                1L,
                new BigDecimal("10000"),
                "출금",
                "입금"
        );

        assertThatThrownBy(() ->
                bankTransferService.transfer(request)
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.SAME_ACCOUNT_TRANSFER.getMessage()
                );

        verifyNoInteractions(transferProcessor);
    }

    private TransferRequest createRequest(String requestKey) {
        return new TransferRequest(
                requestKey,
                "USER_001",
                1L,
                2L,
                new BigDecimal("10000"),
                "테스트 출금",
                "테스트 입금"
        );
    }
}