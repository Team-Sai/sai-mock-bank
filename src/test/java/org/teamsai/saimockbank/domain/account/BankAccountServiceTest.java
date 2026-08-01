package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountMapper bankAccountMapper;

    @InjectMocks
    private BankAccountService bankAccountService;

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

        when(account.getUserKey()).thenReturn("USER_002");
        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
                bankAccountService.getAccount(1L, "USER_001")
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        AccountErrorCode.ACCOUNT_ACCESS_DENIED.getMessage()
                );
    }
}