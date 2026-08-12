package org.teamsai.saimockbank.domain.transfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.TransferProcessor;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferProcessor 단위 테스트")
class TransferProcessorTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private BankTransferMapper bankTransferMapper;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @InjectMocks
    private TransferProcessor transferProcessor;

    private static final Long LOGIN_USER_ID = 100L;
    private static final Long FROM_ACCOUNT_ID = 1L;
    private static final Long TO_ACCOUNT_ID = 2L;

    private TransferRequest createRequest(BigDecimal amount) {
        return new TransferRequest(
                "TX-REQ-001",
                FROM_ACCOUNT_ID,
                "110-234-567890",
                amount,
                "테스트 출금",
                "테스트 입금"
        );
    }

    private BankAccountDTO createAccount(
            Long accountId, Long bankUserId, BigDecimal balance, AccountStatus status
    ) {
        return BankAccountDTO.builder()
                .accountId(accountId)
                .bankUserId(bankUserId)
                .bankCode("088")
                .accountNumber("110-111-" + accountId)
                .accountName("테스트 계좌")
                .accountHolderName("홍길동")
                .balance(balance)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("process(request, loginUserId, toAccountId)")
    class Process {

        @Test
        @DisplayName("로그인 사용자가 출금 계좌 소유자가 아니면 ACCOUNT_ACCESS_DENIED 예외가 발생한다")
        void throwsWhenNotOwner() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            BankAccountDTO fromAccount = createAccount(FROM_ACCOUNT_ID, 999L, BigDecimal.valueOf(100000), AccountStatus.ACTIVE);
            BankAccountDTO toAccount = createAccount(TO_ACCOUNT_ID, 200L, BigDecimal.valueOf(50000), AccountStatus.ACTIVE);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
            when(bankAccountMapper.findByIdForUpdate(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.ACCOUNT_ACCESS_DENIED.getMessage());

            verify(bankAccountMapper, never()).decreaseBalance(any(), any());
            verifyNoInteractions(bankTransferMapper);
            verifyNoInteractions(bankTransactionMapper);
        }

        @Test
        @DisplayName("출금 계좌와 입금 계좌가 동일하면 SAME_ACCOUNT_TRANSFER 예외가 발생한다")
        void throwsWhenSameAccount() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            BankAccountDTO account = createAccount(FROM_ACCOUNT_ID, LOGIN_USER_ID, BigDecimal.valueOf(100000), AccountStatus.ACTIVE);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(account));

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, FROM_ACCOUNT_ID) // toAccountId == fromAccountId
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.SAME_ACCOUNT_TRANSFER.getMessage());

            verify(bankAccountMapper, never()).decreaseBalance(any(), any());
            verifyNoInteractions(bankTransferMapper);
            verifyNoInteractions(bankTransactionMapper);
        }

        @Test
        @DisplayName("출금 계좌가 비활성 상태이면 ACCOUNT_UNAVAILABLE 예외가 발생한다")
        void throwsWhenFromAccountInactive() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            BankAccountDTO fromAccount = createAccount(FROM_ACCOUNT_ID, LOGIN_USER_ID, BigDecimal.valueOf(100000), AccountStatus.SUSPENDED);
            BankAccountDTO toAccount = createAccount(TO_ACCOUNT_ID, 200L, BigDecimal.valueOf(50000), AccountStatus.ACTIVE);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
            when(bankAccountMapper.findByIdForUpdate(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.ACCOUNT_UNAVAILABLE.getMessage());
        }

        @Test
        @DisplayName("입금 계좌가 비활성 상태이면 ACCOUNT_UNAVAILABLE 예외가 발생한다")
        void throwsWhenToAccountInactive() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            BankAccountDTO fromAccount = createAccount(FROM_ACCOUNT_ID, LOGIN_USER_ID, BigDecimal.valueOf(100000), AccountStatus.ACTIVE);
            BankAccountDTO toAccount = createAccount(TO_ACCOUNT_ID, 200L, BigDecimal.valueOf(50000), AccountStatus.SUSPENDED);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
            when(bankAccountMapper.findByIdForUpdate(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.ACCOUNT_UNAVAILABLE.getMessage());
        }

        @Test
        @DisplayName("잔액이 이체 금액보다 부족하면 INSUFFICIENT_BALANCE 예외가 발생한다")
        void throwsWhenInsufficientBalance() {
            TransferRequest request = createRequest(BigDecimal.valueOf(100000));

            BankAccountDTO fromAccount = createAccount(FROM_ACCOUNT_ID, LOGIN_USER_ID, BigDecimal.valueOf(50000), AccountStatus.ACTIVE);
            BankAccountDTO toAccount = createAccount(TO_ACCOUNT_ID, 200L, BigDecimal.valueOf(50000), AccountStatus.ACTIVE);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
            when(bankAccountMapper.findByIdForUpdate(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.INSUFFICIENT_BALANCE.getMessage());
        }

        @Test
        @DisplayName("모든 검증을 통과하면 잔액을 갱신하고 이체/거래내역을 저장한다")
        void processesTransferSuccessfully() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            BankAccountDTO fromAccount = createAccount(FROM_ACCOUNT_ID, LOGIN_USER_ID, BigDecimal.valueOf(100000), AccountStatus.ACTIVE);
            BankAccountDTO toAccount = createAccount(TO_ACCOUNT_ID, 200L, BigDecimal.valueOf(50000), AccountStatus.ACTIVE);

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
            when(bankAccountMapper.findByIdForUpdate(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
            when(bankAccountMapper.decreaseBalance(FROM_ACCOUNT_ID, request.amount())).thenReturn(1);
            when(bankAccountMapper.increaseBalance(TO_ACCOUNT_ID, request.amount())).thenReturn(1);
            when(bankTransferMapper.insert(any(BankTransferDTO.class))).thenReturn(1);
            when(bankTransactionMapper.insert(any())).thenReturn(1);

            BankTransferDTO result = transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID);

            assertThat(result.getFromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
            assertThat(result.getToAccountId()).isEqualTo(TO_ACCOUNT_ID);
            assertThat(result.getAmount()).isEqualByComparingTo("10000");

            verify(bankAccountMapper).decreaseBalance(FROM_ACCOUNT_ID, request.amount());
            verify(bankAccountMapper).increaseBalance(TO_ACCOUNT_ID, request.amount());
            verify(bankTransferMapper).insert(any(BankTransferDTO.class));
            verify(bankTransactionMapper, times(2)).insert(any()); // 출금 1건 + 입금 1건
        }

        @Test
        @DisplayName("존재하지 않는 계좌로 이체를 시도하면 ACCOUNT_NOT_FOUND 예외가 발생한다")
        void throwsWhenAccountNotFound() {
            TransferRequest request = createRequest(BigDecimal.valueOf(10000));

            when(bankAccountMapper.findByIdForUpdate(FROM_ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transferProcessor.process(request, LOGIN_USER_ID, TO_ACCOUNT_ID)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(TransferErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }
    }
}