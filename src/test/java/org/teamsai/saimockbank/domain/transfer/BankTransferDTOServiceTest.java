
package org.teamsai.saimockbank.domain.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;
import org.teamsai.saimockbank.domain.transfer.service.FdsTransferCommandService;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransferDTOServiceTest {

    @Mock
    private BankTransferMapper bankTransferMapper;

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private FdsTransferCommandService commandService;

    @Mock
    private FdsPreTransferDecisionService fdsDecisionService;

    @InjectMocks
    private BankTransferService bankTransferService;

    private static final Long LOGIN_USER_ID = 100L;
    private static final String TO_ACCOUNT_NUMBER = "110-234-567890";

    @Test
    void 계좌_이체를_실행한다() {
        TransferRequest request = createRequest("TRANSFER-001");

        BankAccountDTO fromAccount =
                createAccount(1L, LOGIN_USER_ID, "110-111-111111");

        BankAccountDTO toAccount =
                createAccount(2L, 200L, TO_ACCOUNT_NUMBER);

        BankTransferDTO transfer = createTransfer(
                "TRANSFER-001",
                TransferStatus.SUCCESS
        );

        FdsContext context = mock(FdsContext.class);

        FdsPreTransferDecisionService.Decision decision =
                new FdsPreTransferDecisionService.Decision(
                        context,
                        FdsRiskLevel.LOW
                );

        when(bankAccountMapper.findByAccountNumber(TO_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(toAccount));

        when(bankTransferMapper.findByRequestKey("TRANSFER-001"))
                .thenReturn(Optional.empty());

        when(fdsDecisionService.assess(
                1L,
                2L,
                new BigDecimal("10000")
        )).thenReturn(decision);

        when(commandService.execute(
                LOGIN_USER_ID,
                request,
                2L,
                decision
        )).thenReturn(transfer);

        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(bankAccountMapper.findById(2L))
                .thenReturn(Optional.of(toAccount));

        var result = bankTransferService.transfer(
                LOGIN_USER_ID,
                request
        );

        assertThat(result.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(result.amount())
                .isEqualByComparingTo("10000");

        verify(fdsDecisionService).assess(
                1L,
                2L,
                new BigDecimal("10000")
        );

        verify(commandService).execute(
                LOGIN_USER_ID,
                request,
                2L,
                decision
        );
    }

    @Test
    void 존재하지_않는_이체는_예외가_발생한다() {
        when(bankTransferMapper.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bankTransferService.getTransfer(
                        LOGIN_USER_ID,
                        999L
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.TRANSFER_NOT_FOUND
                                .getMessage()
                );
    }

    @Test
    void 이체_당사자가_아니면_조회할_수_없다() {
        BankTransferDTO transfer = createTransfer(
                "TRANSFER-003",
                TransferStatus.SUCCESS
        );

        BankAccountDTO fromAccount =
                createAccount(1L, 300L, "110-111-111111");

        BankAccountDTO toAccount =
                createAccount(2L, 400L, TO_ACCOUNT_NUMBER);

        when(bankTransferMapper.findById(1L))
                .thenReturn(Optional.of(transfer));

        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(bankAccountMapper.findById(2L))
                .thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() ->
                bankTransferService.getTransfer(
                        LOGIN_USER_ID,
                        1L
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.ACCOUNT_ACCESS_DENIED
                                .getMessage()
                );
    }

    @Test
    void 받는_계좌번호가_존재하지_않으면_예외가_발생한다() {
        TransferRequest request = createRequest("TRANSFER-004");

        when(bankAccountMapper.findByAccountNumber(TO_ACCOUNT_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                bankTransferService.transfer(
                        LOGIN_USER_ID,
                        request
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.ACCOUNT_NOT_FOUND
                                .getMessage()
                );

        verifyNoInteractions(
                commandService,
                fdsDecisionService
        );
    }

    @Test
    void 다른_사용자가_동일한_requestKey로_요청하면_소유권_예외가_발생한다() {
        TransferRequest request = createRequest("TRANSFER-005");

        BankAccountDTO toAccount =
                createAccount(2L, 200L, TO_ACCOUNT_NUMBER);

        BankTransferDTO existingTransfer = createTransfer(
                "TRANSFER-005",
                TransferStatus.SUCCESS
        );

        BankAccountDTO fromAccount =
                createAccount(1L, 300L, "110-111-111111");

        BankAccountDTO toAccountForOwnerCheck =
                createAccount(2L, 400L, TO_ACCOUNT_NUMBER);

        when(bankAccountMapper.findByAccountNumber(TO_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(toAccount));

        when(bankTransferMapper.findByRequestKey("TRANSFER-005"))
                .thenReturn(Optional.of(existingTransfer));

        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(bankAccountMapper.findById(2L))
                .thenReturn(Optional.of(toAccountForOwnerCheck));

        assertThatThrownBy(() ->
                bankTransferService.transfer(
                        LOGIN_USER_ID,
                        request
                )
        )
                .isInstanceOf(DomainException.class)
                .hasMessage(
                        TransferErrorCode.ACCOUNT_ACCESS_DENIED
                                .getMessage()
                );

        verifyNoInteractions(
                commandService,
                fdsDecisionService
        );
    }

    @Test
    void 본인이_참여한_이체는_동일한_requestKey로_재요청해도_정상_응답된다() {
        TransferRequest request = createRequest("TRANSFER-006");

        BankAccountDTO toAccount =
                createAccount(2L, 200L, TO_ACCOUNT_NUMBER);

        BankAccountDTO fromAccount =
                createAccount(1L, LOGIN_USER_ID, "110-111-111111");

        BankTransferDTO existingTransfer = createTransfer(
                "TRANSFER-006",
                TransferStatus.SUCCESS
        );

        when(bankAccountMapper.findByAccountNumber(TO_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(toAccount));

        when(bankTransferMapper.findByRequestKey("TRANSFER-006"))
                .thenReturn(Optional.of(existingTransfer));

        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(bankAccountMapper.findById(2L))
                .thenReturn(Optional.of(toAccount));

        var result = bankTransferService.transfer(
                LOGIN_USER_ID,
                request
        );

        assertThat(result.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(result.amount())
                .isEqualByComparingTo("10000");

        verifyNoInteractions(
                commandService,
                fdsDecisionService
        );
    }

    @Test
    void 고위험_이체는_심사대기_상태를_반환한다() {
        TransferRequest request = createRequest("TRANSFER-HIGH");

        BankAccountDTO fromAccount =
                createAccount(1L, LOGIN_USER_ID, "110-111-111111");

        BankAccountDTO toAccount =
                createAccount(2L, 200L, TO_ACCOUNT_NUMBER);

        BankTransferDTO pending = createTransfer(
                "TRANSFER-HIGH",
                TransferStatus.PENDING_REVIEW
        );

        FdsContext context = mock(FdsContext.class);

        FdsPreTransferDecisionService.Decision decision =
                new FdsPreTransferDecisionService.Decision(
                        context,
                        FdsRiskLevel.HIGH
                );

        when(bankAccountMapper.findByAccountNumber(TO_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(toAccount));

        when(bankTransferMapper.findByRequestKey("TRANSFER-HIGH"))
                .thenReturn(Optional.empty());

        when(fdsDecisionService.assess(
                1L,
                2L,
                new BigDecimal("10000")
        )).thenReturn(decision);

        when(commandService.execute(
                LOGIN_USER_ID,
                request,
                2L,
                decision
        )).thenReturn(pending);

        when(bankAccountMapper.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(bankAccountMapper.findById(2L))
                .thenReturn(Optional.of(toAccount));

        var result = bankTransferService.transfer(
                LOGIN_USER_ID,
                request
        );

        assertThat(result.status())
                .isEqualTo(TransferStatus.PENDING_REVIEW);

        assertThat(result.completedAt())
                .isNull();

        verify(commandService).execute(
                LOGIN_USER_ID,
                request,
                2L,
                decision
        );
    }

    private BankTransferDTO createTransfer(
            String requestKey,
            TransferStatus status
    ) {
        return BankTransferDTO.builder()
                .requestKey(requestKey)
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(new BigDecimal("10000"))
                .status(status)
                .completedAt(
                        status == TransferStatus.SUCCESS
                                ? LocalDateTime.now()
                                : null
                )
                .build();
    }

    private TransferRequest createRequest(String requestKey) {
        return new TransferRequest(
                requestKey,
                1L,
                TO_ACCOUNT_NUMBER,
                new BigDecimal("10000"),
                "테스트 출금",
                "테스트 입금"
        );
    }

    private BankAccountDTO createAccount(
            Long accountId,
            Long bankUserId,
            String accountNumber
    ) {
        return BankAccountDTO.builder()
                .accountId(accountId)
                .bankUserId(bankUserId)
                .bankCode("088")
                .accountNumber(accountNumber)
                .accountName("테스트 계좌")
                .accountHolderName("홍길동")
                .balance(new BigDecimal("100000"))
                .status(AccountStatus.ACTIVE)
                .build();
    }
}
