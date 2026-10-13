package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAccountService 단위 테스트")
class BankAccountDTOServiceTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private UserKeyHasher userKeyHasher;

    @Mock
    private AccountOwnershipValidator accountOwnershipValidator;

    @InjectMocks
    private BankAccountService bankAccountService;

    private static final String RAW_USER_KEY = "USER_001";
    private static final String HASHED_USER_KEY = "HASHED_USER_001";

    @Nested
    @DisplayName("getAccounts(userKey)")
    class GetAccounts {

        @Test
        @DisplayName("원본 키를 해싱해서 그 값으로 계좌 목록을 조회한다")
        void 사용자_계좌_목록을_조회한다() {
            BankAccountDTO account = mock(BankAccountDTO.class);
            given(account.getBankCode()).willReturn("088");

            given(userKeyHasher.hash(RAW_USER_KEY)).willReturn(HASHED_USER_KEY);
            given(bankAccountMapper.findAllByUserKey(HASHED_USER_KEY))
                    .willReturn(List.of(account));

            var result = bankAccountService.getAccounts(RAW_USER_KEY);

            assertThat(result).hasSize(1);
            verify(userKeyHasher).hash(RAW_USER_KEY);
            verify(bankAccountMapper).findAllByUserKey(HASHED_USER_KEY);
        }
    }

    @Nested
    @DisplayName("getAccount(accountId, userKey)")
    class GetAccount {

        @Test
        @DisplayName("존재하지 않는 계좌는 예외가 발생한다")
        void 존재하지_않는_계좌는_예외가_발생한다() {
            given(bankAccountMapper.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    bankAccountService.getAccount(999L, RAW_USER_KEY)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(AccountErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 계좌는 조회할 수 없다")
        void 다른_사용자의_계좌는_조회할_수_없다() {
            BankAccountDTO account = mock(BankAccountDTO.class);

            given(bankAccountMapper.findById(1L)).willReturn(Optional.of(account));
            given(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .willReturn(Optional.of("HASHED_USER_002"));

            willThrow(AccountErrorCode.ACCOUNT_ACCESS_DENIED.toException())
                    .given(accountOwnershipValidator)
                    .verify(eq("HASHED_USER_002"), eq(RAW_USER_KEY), any());

            assertThatThrownBy(() ->
                    bankAccountService.getAccount(1L, RAW_USER_KEY)
            )
                    .isInstanceOf(DomainException.class)
                    .hasMessage(AccountErrorCode.ACCOUNT_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("본인 소유 계좌면 정상적으로 반환한다")
        void 본인_소유_계좌는_정상_반환된다() {
            BankAccountDTO account = mock(BankAccountDTO.class);
            given(account.getBankCode()).willReturn("088");

            given(bankAccountMapper.findById(1L)).willReturn(Optional.of(account));
            given(bankAccountMapper.findOwnerUserKeyHashByAccountId(1L))
                    .willReturn(Optional.of(HASHED_USER_KEY));
            var result = bankAccountService.getAccount(1L, RAW_USER_KEY);

            assertThat(result).isNotNull();
            verify(accountOwnershipValidator).verify(eq(HASHED_USER_KEY), eq(RAW_USER_KEY), any());
        }
    }
}