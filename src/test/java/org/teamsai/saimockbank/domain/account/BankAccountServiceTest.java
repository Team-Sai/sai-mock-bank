package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.account.dto.AccountDetailResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.identity.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAccountService 단위 테스트")
class BankAccountServiceTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private UserKeyHasher userKeyHasher;

    @InjectMocks
    private BankAccountService bankAccountService;

    private static final String RAW_USER_KEY = "raw-user-key";
    private static final String HASHED_USER_KEY = "hashed-user-key";


    private BankAccountDTO createAccount(Long accountId) {
        BankAccountDTO account = new BankAccountDTO();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        ReflectionTestUtils.setField(account, "bankCode", "088");
        ReflectionTestUtils.setField(account, "accountNumber", "1234567890123");
        ReflectionTestUtils.setField(account, "accountName", "테스트계좌");
        ReflectionTestUtils.setField(account, "accountHolderName", "홍길동");
        ReflectionTestUtils.setField(account, "balance", BigDecimal.valueOf(10000));
        ReflectionTestUtils.setField(account, "status", AccountStatus.ACTIVE);
        return account;
    }

    @Nested
    @DisplayName("getAccounts(userKey)")
    class GetAccounts {

        @Test
        void 사용자_계좌_목록을_조회한다() {
            BankAccountDTO account = mock(BankAccountDTO.class);

            when(bankAccountMapper.findAllByUserKey("USER_001"))
                    .thenReturn(List.of(account));

            var result = bankAccountService.getAccounts("USER_001");

            assertThat(result).hasSize(1);
            verify(bankAccountMapper).findAllByUserKey("USER_001");
        }

        @Test
        void 존재하지_않는_계좌는_예외가_발생한다() {
            when(bankAccountMapper.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    bankAccountService.getAccount(999L, "USER_001")
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            AccountErrorCode.ACCOUNT_NOT_FOUND.getMessage()
                    );
        }

        @Test
        void 다른_사용자의_계좌는_조회할_수_없다() {
            BankAccountDTO account = mock(BankAccountDTO.class);

            when(bankAccountMapper.findById(1L))
                    .thenReturn(Optional.of(account));
            when(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .thenReturn(Optional.of("HASHED_USER_002")); // 계좌 실제 주인
            when(userKeyHasher.hash("USER_001")).thenReturn("HASHED_USER_001"); // 요청자

            assertThatThrownBy(() ->
                    bankAccountService.getAccount(1L, "USER_001")
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(
                            AccountErrorCode.ACCOUNT_ACCESS_DENIED.getMessage()
                    );
        }

        @Test
        @DisplayName("정상적인 userKey가 주어지면 해싱 후 계좌 목록을 반환한다")
        void returnsAccountListWhenUserKeyIsValid() {
            BankAccountDTO account1 = createAccount(1L);
            BankAccountDTO account2 = createAccount(2L);

            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);
            given(bankAccountMapper.findAllByUserKey(HASHED_USER_KEY))
                    .willReturn(List.of(account1, account2));

            List<AccountListResponse> result = bankAccountService.getAccounts(RAW_USER_KEY);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).accountId()).isEqualTo(1L);
            assertThat(result.get(0).bankName()).isEqualTo("신한은행");
            verify(userKeyHasher).hash(RAW_USER_KEY);
            verify(bankAccountMapper).findAllByUserKey(HASHED_USER_KEY);
        }

        @Test
        @DisplayName("조회된 계좌가 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoAccountsFound() {
            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);
            given(bankAccountMapper.findAllByUserKey(HASHED_USER_KEY)).willReturn(List.of());

            List<AccountListResponse> result = bankAccountService.getAccounts(RAW_USER_KEY);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("userKey가 null이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 해싱/조회는 호출되지 않는다")
        void throwsWhenUserKeyIsNull() {
            assertThatThrownBy(() -> bankAccountService.getAccounts(null))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(userKeyHasher, never()).hash(anyString());
            verify(bankAccountMapper, never()).findAllByUserKey(anyString());
        }

        @Test
        @DisplayName("userKey가 공백이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 해싱/조회는 호출되지 않는다")
        void throwsWhenUserKeyIsBlank() {
            assertThatThrownBy(() -> bankAccountService.getAccounts("   "))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(userKeyHasher, never()).hash(anyString());
            verify(bankAccountMapper, never()).findAllByUserKey(anyString());
        }
    }

    @Nested
    @DisplayName("getAccount(accountId, userKey)")
    class GetAccount {

        @Test
        @DisplayName("본인 소유 계좌를 조회하면 해싱 후 비교하여 상세 정보를 반환한다")
        void returnsAccountDetailWhenOwnerMatches() {
            BankAccountDTO account = createAccount(1L);
            given(bankAccountMapper.findById(1L)).willReturn(Optional.of(account));
            given(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .willReturn(Optional.of(HASHED_USER_KEY));
            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);

            AccountDetailResponse result = bankAccountService.getAccount(1L, RAW_USER_KEY);

            assertThat(result.accountId()).isEqualTo(1L);
            assertThat(result.bankCode()).isEqualTo("신한은행");
            verify(bankAccountMapper).findById(1L);
            verify(bankAccountMapper).findOwnerUserKeyHashByAccountId(1L);
            verify(userKeyHasher).hash(RAW_USER_KEY);
        }

        @Test
        @DisplayName("존재하지 않는 계좌면 ACCOUNT_NOT_FOUND 예외가 발생하고 해싱/소유자 조회는 호출되지 않는다")
        void throwsWhenAccountNotFound() {
            given(bankAccountMapper.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> bankAccountService.getAccount(999L, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(bankAccountMapper, never()).findOwnerUserKeyHashByAccountId(anyLong());
            verify(userKeyHasher, never()).hash(anyString());
        }

        @Test
        @DisplayName("소유자가 다른 계좌를 조회하면 해싱 후 비교하여 ACCOUNT_ACCESS_DENIED 예외가 발생한다")
        void throwsWhenUserKeyMismatch() {
            BankAccountDTO account = createAccount(1L);
            given(bankAccountMapper.findById(1L)).willReturn(Optional.of(account));
            given(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .willReturn(Optional.of("other-hashed-user-key"));
            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);

            assertThatThrownBy(() -> bankAccountService.getAccount(1L, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);

            verify(userKeyHasher).hash(RAW_USER_KEY);
        }

        @Test
        @DisplayName("계좌는 있지만 연결된 identity의 해시값을 찾을 수 없으면 ACCOUNT_ACCESS_DENIED 예외가 발생한다")
        void throwsWhenOwnerHashNotFound() {
            BankAccountDTO account = createAccount(1L);
            given(bankAccountMapper.findById(1L)).willReturn(Optional.of(account));
            given(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> bankAccountService.getAccount(1L, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("accountId가 null이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 mapper/해싱은 호출되지 않는다")
        void throwsWhenAccountIdIsNull() {
            assertThatThrownBy(() -> bankAccountService.getAccount(null, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(bankAccountMapper, never()).findById(anyLong());
            verify(bankAccountMapper, never()).findOwnerUserKeyHashByAccountId(anyLong());
            verify(userKeyHasher, never()).hash(anyString());
        }
        @Test
        @DisplayName("accountId가 0 이하이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 mapper/해싱은 호출되지 않는다")
        void throwsWhenAccountIdIsNotPositive() {
            assertThatThrownBy(() -> bankAccountService.getAccount(0L, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            assertThatThrownBy(() -> bankAccountService.getAccount(-1L, RAW_USER_KEY))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(bankAccountMapper, never()).findById(anyLong());
            verify(bankAccountMapper, never()).findOwnerUserKeyHashByAccountId(anyLong());
            verify(userKeyHasher, never()).hash(anyString());
        }

        @Test
        @DisplayName("userKey가 null이거나 공백이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 mapper/해싱은 호출되지 않는다")
        void throwsWhenUserKeyIsNullOrBlank() {
            assertThatThrownBy(() -> bankAccountService.getAccount(1L, null))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            assertThatThrownBy(() -> bankAccountService.getAccount(1L, "   "))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(bankAccountMapper, never()).findById(anyLong());
            verify(bankAccountMapper, never()).findOwnerUserKeyHashByAccountId(anyLong());
            verify(userKeyHasher, never()).hash(anyString());
        }
    }

    @Nested
    @DisplayName("getAccountsByUserKey(userKey)")
    class GetAccountsByUserKey {

        @Test
        @DisplayName("원본 userKey를 해싱한 뒤 그 값으로 계좌 목록을 조회한다")
        void returnsAccountsAfterHashing() {
            BankAccountDTO account = createAccount(1L);
            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);
            given(bankAccountMapper.findByUserKey(HASHED_USER_KEY)).willReturn(List.of(account));

            List<BankAccountDTO> result = bankAccountService.getAccountsByUserKey(RAW_USER_KEY);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAccountId()).isEqualTo(1L);
            verify(userKeyHasher).hash(RAW_USER_KEY);
            verify(bankAccountMapper).findByUserKey(HASHED_USER_KEY);
        }

        @Test
        @DisplayName("조회된 계좌가 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoAccountsFound() {
            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);
            given(bankAccountMapper.findByUserKey(HASHED_USER_KEY)).willReturn(List.of());

            List<BankAccountDTO> result = bankAccountService.getAccountsByUserKey(RAW_USER_KEY);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("userKey가 null이거나 공백이면 INVALID_ACCOUNT_REQUEST 예외가 발생하고 해싱/조회는 호출되지 않는다")
        void throwsWhenUserKeyIsNullOrBlank() {
            assertThatThrownBy(() -> bankAccountService.getAccountsByUserKey(null))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            assertThatThrownBy(() -> bankAccountService.getAccountsByUserKey("   "))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_REQUEST);

            verify(userKeyHasher, never()).hash(anyString());
            verify(bankAccountMapper, never()).findByUserKey(anyString());
        }
    }
}