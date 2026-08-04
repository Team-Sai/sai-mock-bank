package org.teamsai.saimockbank.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.AccountWithOwnerDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionService 단위 테스트")
class BankTransactionServiceTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @Mock
    private AccountOwnershipValidator ownershipValidator;

    @InjectMocks
    private BankTransactionService bankTransactionService;

    private static final String RAW_USER_KEY = "USER_001";
    private static final String HASHED_USER_KEY = "HASHED_USER_001";

    private AccountWithOwnerDTO createAccountWithOwner(Long accountId, String ownerHash) {
        AccountWithOwnerDTO account = new AccountWithOwnerDTO();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        ReflectionTestUtils.setField(account, "bankCode", "088");
        ReflectionTestUtils.setField(account, "accountNumber", "1234567890123");
        ReflectionTestUtils.setField(account, "accountName", "테스트계좌");
        ReflectionTestUtils.setField(account, "accountHolderName", "홍길동");
        ReflectionTestUtils.setField(account, "balance", BigDecimal.valueOf(10000));
        ReflectionTestUtils.setField(account, "status", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "ownerUserKeyHash", ownerHash);
        return account;
    }

    @Nested
    @DisplayName("getTransactions(accountId, userKey, afterTransactionId)")
    class GetTransactions {

        @Test
        void 특정_거래ID_이후의_거래내역을_조회한다() {
            AccountWithOwnerDTO account = createAccountWithOwner(1L, HASHED_USER_KEY);

            when(bankAccountMapper.findByIdWithOwner(1L))
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
                    RAW_USER_KEY,
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
            verify(ownershipValidator)
                    .verify(eq(HASHED_USER_KEY), eq(RAW_USER_KEY), any());
        }

        @Test
        void 거래ID를_전달하지_않으면_처음부터_조회한다() {
            AccountWithOwnerDTO account = createAccountWithOwner(1L, HASHED_USER_KEY);

            when(bankAccountMapper.findByIdWithOwner(1L))
                    .thenReturn(Optional.of(account));

            when(bankTransactionMapper.findAllByAccountIdAfter(1L, 0L))
                    .thenReturn(List.of());

            var result = bankTransactionService.getTransactions(
                    1L,
                    RAW_USER_KEY,
                    null
            );

            assertThat(result).isEmpty();

            verify(bankTransactionMapper)
                    .findAllByAccountIdAfter(1L, 0L);
        }

        @Test
        void 다른_사용자의_거래내역은_조회할_수_없다() {
            AccountWithOwnerDTO account = createAccountWithOwner(1L, "HASHED_USER_002"); // 계좌 실제 주인은 USER_002

            when(bankAccountMapper.findByIdWithOwner(1L))
                    .thenReturn(Optional.of(account));

            willThrow(TransactionErrorCode.ACCOUNT_ACCESS_DENIED.toException())
                    .given(ownershipValidator)
                    .verify(eq("HASHED_USER_002"), eq(RAW_USER_KEY), any());

            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(
                            1L,
                            RAW_USER_KEY,
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
            when(bankAccountMapper.findByIdWithOwner(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(
                            999L,
                            RAW_USER_KEY,
                            0L
                    )
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.ACCOUNT_NOT_FOUND
                                    .getMessage()
                    );

            verifyNoInteractions(ownershipValidator);
            verifyNoInteractions(bankTransactionMapper);
        }

        @Test
        @DisplayName("accountId가 null이면 INVALID_TRANSACTION_REQUEST 예외가 발생하고 mapper/validator는 호출되지 않는다")
        void throwsWhenAccountIdIsNull() {
            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(null, RAW_USER_KEY, 0L)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.INVALID_TRANSACTION_REQUEST
                                    .getMessage()
                    );

            verifyNoInteractions(bankAccountMapper);
            verifyNoInteractions(ownershipValidator);
            verifyNoInteractions(bankTransactionMapper);
        }

        @Test
        @DisplayName("accountId가 0 이하이면 INVALID_TRANSACTION_REQUEST 예외가 발생하고 mapper/validator는 호출되지 않는다")
        void throwsWhenAccountIdIsNotPositive() {
            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(0L, RAW_USER_KEY, 0L)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.INVALID_TRANSACTION_REQUEST
                                    .getMessage()
                    );

            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(-1L, RAW_USER_KEY, 0L)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.INVALID_TRANSACTION_REQUEST
                                    .getMessage()
                    );

            verifyNoInteractions(bankAccountMapper);
            verifyNoInteractions(ownershipValidator);
            verifyNoInteractions(bankTransactionMapper);
        }

        @Test
        @DisplayName("userKey가 null이거나 공백이면 INVALID_TRANSACTION_REQUEST 예외가 발생하고 mapper/validator는 호출되지 않는다")
        void throwsWhenUserKeyIsNullOrBlank() {
            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(1L, null, 0L)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.INVALID_TRANSACTION_REQUEST
                                    .getMessage()
                    );

            assertThatThrownBy(() ->
                    bankTransactionService.getTransactions(1L, "   ", 0L)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            TransactionErrorCode.INVALID_TRANSACTION_REQUEST
                                    .getMessage()
                    );

            verifyNoInteractions(bankAccountMapper);
            verifyNoInteractions(ownershipValidator);
            verifyNoInteractions(bankTransactionMapper);
        }
    }
}