package org.teamsai.saimockbank.domain.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionType;
import org.teamsai.saimockbank.domain.transaction.exception.TransactionErrorCode;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saimockbank.domain.transaction.service.BankTransactionService;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransactionServiceTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @InjectMocks
    private BankTransactionService bankTransactionService;

    @Test
    void 특정_거래ID_이후의_거래내역을_조회한다() {
        BankAccountDTO account = mock(BankAccountDTO.class);

        when(account.getUserKey()).thenReturn("USER_001");
        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(account));

        BankTransactionDTO transaction = BankTransactionDTO.builder()
                .transactionKey("TX-001")
                .transactionType(TransactionType.WITHDRAW)
                .amount(new BigDecimal("10000"))
                .balanceAfter(new BigDecimal("90000"))
                .counterpartyName("홍길동")
                .counterpartyAccountNumber("110123456789")
                .memo("테스트 이체")
                .transactionAt(LocalDateTime.now())
                .transferId(1L)
                .accountId(1L)
                .build();

        when(bankTransactionMapper.findAllByAccountIdAfter(1L, 10L))
                .thenReturn(List.of(transaction));

        var result = bankTransactionService.getTransactions(
                1L,
                "USER_001",
                10L
        );

        assertThat(result).hasSize(1);

        var response = result.get(0);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.transactionType())
                .isEqualTo(TransactionType.WITHDRAW);
        assertThat(response.amount())
                .isEqualByComparingTo("10000");
        assertThat(response.counterpartyName())
                .isEqualTo("홍길동");
        assertThat(response.maskedCounterpartyAccountNumber())
                .isEqualTo("********6789");
        assertThat(response.memo())
                .isEqualTo("테스트 이체");

        verify(bankTransactionMapper)
                .findAllByAccountIdAfter(1L, 10L);
    }

    @Test
    void 거래ID를_전달하지_않으면_처음부터_조회한다() {
        BankAccountDTO account = mock(BankAccountDTO.class);

        when(account.getUserKey()).thenReturn("USER_001");
        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(account));

        when(bankTransactionMapper.findAllByAccountIdAfter(1L, 0L))
                .thenReturn(List.of());

        var result = bankTransactionService.getTransactions(
                1L,
                "USER_001",
                null
        );

        assertThat(result).isEmpty();

        verify(bankTransactionMapper)
                .findAllByAccountIdAfter(1L, 0L);
    }

    @Test
    void 다른_사용자의_거래내역은_조회할_수_없다() {
        BankAccountDTO account = mock(BankAccountDTO.class);

        when(account.getUserKey()).thenReturn("USER_002");
        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
                bankTransactionService.getTransactions(
                        1L,
                        "USER_001",
                        0L
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransactionErrorCode.ACCOUNT_ACCESS_DENIED
                                .getMessage()
                );

        verifyNoInteractions(bankTransactionMapper);
    }

    @Test
    void 존재하지_않는_계좌의_거래내역은_조회할_수_없다() {
        when(bankAccountMapper.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bankTransactionService.getTransactions(
                        999L,
                        "USER_001",
                        0L
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransactionErrorCode.ACCOUNT_NOT_FOUND
                                .getMessage()
                );

        verifyNoInteractions(bankTransactionMapper);
    }
}