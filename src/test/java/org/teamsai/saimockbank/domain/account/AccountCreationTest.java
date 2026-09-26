package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.account.dto.*;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountCreationTest {
    @Test void customBankAndInitialBalanceAreNormalizedAndFingerprintProtected() {
        var request = new CreateAccountRequest("CUSTOM", " 생활비 ", " 테스트은행 ", new BigDecimal("12345.60"));
        var canonical = request.normalized();
        assertThat(canonical.bankName()).isEqualTo("테스트은행");
        assertThat(canonical.initialBalance()).isEqualByComparingTo("12345.60");
        assertThat(request.fingerprint()).isEqualTo(new CreateAccountRequest("CUSTOM", "생활비", "테스트은행", new BigDecimal("12345.6")).fingerprint());
        assertThat(request.fingerprint()).isNotEqualTo(new CreateAccountRequest("CUSTOM", "생활비", "다른은행", new BigDecimal("12345.60")).fingerprint());
        assertThat(request.fingerprint()).isNotEqualTo(new CreateAccountRequest("CUSTOM", "생활비", "테스트은행", new BigDecimal("12345.61")).fingerprint());
    }

    @Test void invalidInitialBalancesAndCustomNamesAreRejected() {
        for (var amount : List.of("-1", "0.001", "100000000000000000")) {
            assertThatThrownBy(() -> new CreateAccountRequest("088", "입출금통장", null, new BigDecimal(amount)).normalized())
                    .extracting("errorCode").isEqualTo(AccountErrorCode.INVALID_ACCOUNT_CREATION);
        }
        assertThatThrownBy(() -> new CreateAccountRequest("CUSTOM", "계좌", " ", BigDecimal.ONE).normalized())
                .extracting("errorCode").isEqualTo(AccountErrorCode.INVALID_ACCOUNT_CREATION);
        assertThat(new CreateAccountRequest("088", "입출금통장", null, new BigDecimal("99999999999999999.99")).normalized().initialBalance())
                .isEqualByComparingTo("99999999999999999.99");
    }

    @Test void customBankNameAppearsInAllAccountResponses() {
        var account = BankAccountDTO.builder().bankCode("CUSTOM").bankName("테스트은행").build();
        assertThat(AccountDetailResponse.from(account).bankName()).isEqualTo("테스트은행");
        assertThat(AccountListResponse.from(account).bankName()).isEqualTo("테스트은행");
        assertThat(AccountLookupResponse.from(account).bankName()).isEqualTo("테스트은행");
    }

    @Test void accountListDoesNotSwapAccountNameAndHolder() {
        var response = AccountListResponse.from(BankAccountDTO.builder()
                .bankCode("088").accountName("생활비").accountHolderName("홍길동").build());
        assertThat(response.accountName()).isEqualTo("생활비");
        assertThat(response.accountHolderName()).isEqualTo("홍길동");
    }

    final BankAccountMapper accounts = mock(BankAccountMapper.class);
    final UserMapper users = mock(UserMapper.class);
    final BankAccountService service = new BankAccountService(accounts, users,
            mock(UserKeyHasher.class), mock(AccountOwnershipValidator.class));
    final UUID key = UUID.randomUUID();

    private void user() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(
                UserDTO.builder().bankUserId(1L).name("테스트").build()));
    }

    private BankAccountDTO saved(CreateAccountRequest request) {
        return BankAccountDTO.builder().accountId(42L).bankUserId(1L).bankCode("088")
                .accountName("나중에 변경한 이름").accountNumber("0881234567890123")
                .balance(BigDecimal.TEN).status(AccountStatus.ACTIVE)
                .creationRequestHash(request.normalized().fingerprint()).build();
    }

    @Test void createsWithZeroBalanceAndOriginalConditions() {
        user();
        var request = new CreateAccountRequest("004", " 생활비 ");
        when(accounts.insert(any())).thenAnswer(i -> {
            BankAccountDTO account = i.getArgument(0);
            assertThat(account.getBankUserId()).isEqualTo(1L);
            assertThat(account.getBankCode()).isEqualTo("004");
            assertThat(account.getAccountName()).isEqualTo("생활비");
            assertThat(account.getAccountNumber()).startsWith("004").hasSize(16);
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(account.getCreationRequestHash()).isEqualTo(request.normalized().fingerprint());
            ReflectionTestUtils.setField(account, "accountId", 42L);
            return 1;
        });
        when(accounts.findById(42L)).thenReturn(Optional.of(saved(request)));
        assertThat(service.postAccount(1L, key, request).accountId()).isEqualTo(42L);
        verify(accounts).insert(any());
    }

    @Test void sameRequestReplaysWithoutInsertEvenAfterAccountChanges() {
        user();
        when(accounts.findByCreationRequestForUpdate(1L, key.toString()))
                .thenReturn(Optional.of(saved(CreateAccountRequest.defaults())));
        assertThat(service.postAccount(1L, key, null).accountId()).isEqualTo(42L);
        verify(accounts, never()).insert(any());
    }

    @Test void differentNameOrBankWithSameKeyConflictsWithoutInsert() {
        user();
        when(accounts.findByCreationRequestForUpdate(1L, key.toString()))
                .thenReturn(Optional.of(saved(CreateAccountRequest.defaults())));
        for (var request : List.of(new CreateAccountRequest("088", "다른 계좌"),
                new CreateAccountRequest("004", "입출금통장"))) {
            assertThatThrownBy(() -> service.postAccount(1L, key, request))
                    .extracting("errorCode").isEqualTo(AccountErrorCode.ACCOUNT_CREATION_CONFLICT);
        }
        verify(accounts, never()).insert(any());
    }

    @Test void invalidConditionsNeverReachDatabase() {
        for (var request : List.of(new CreateAccountRequest("999", "계좌"),
                new CreateAccountRequest("088", " "), new CreateAccountRequest("088", "a".repeat(101)))) {
            assertThatThrownBy(() -> service.postAccount(1L, key, request))
                    .extracting("errorCode").isEqualTo(AccountErrorCode.INVALID_ACCOUNT_CREATION);
        }
        verifyNoInteractions(accounts, users);
    }

    @Test void normalizationProducesSameFingerprint() {
        assertThat(new CreateAccountRequest(" 088 ", " 입출금통장 ").normalized().fingerprint())
                .isEqualTo(CreateAccountRequest.defaults().fingerprint());
    }

    @Test void accountNumberCollisionRetriesWithoutChangingRequestIdentity() {
        user();
        when(accounts.insert(any())).thenThrow(new DuplicateKeyException("number collision"))
                .thenAnswer(i -> { ReflectionTestUtils.setField((BankAccountDTO) i.getArgument(0), "accountId", 42L); return 1; });
        when(accounts.findById(42L)).thenReturn(Optional.of(saved(CreateAccountRequest.defaults())));
        service.postAccount(1L, key, null);
        verify(accounts, times(2)).insert(argThat(a -> a.getCreationRequestId().equals(key.toString())));
    }
}
