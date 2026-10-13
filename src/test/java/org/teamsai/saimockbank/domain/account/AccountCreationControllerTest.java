package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.teamsai.saimockbank.domain.account.controller.BankAccountController;
import org.teamsai.saimockbank.domain.account.dto.*;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.GlobalExceptionHandler;
import org.teamsai.saimockbank.global.security.CustomUserDetails;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountCreationControllerTest {
    final BankAccountMapper accounts = mock(BankAccountMapper.class);
    final UserMapper users = mock(UserMapper.class);
    final UUID key = UUID.randomUUID();
    MockMvc mvc;

    @BeforeEach void setup() {
        var user = UserDTO.builder().bankUserId(1L).name("테스트").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(accounts.findByCreationRequestForUpdate(1L, key.toString())).thenReturn(Optional.of(
                BankAccountDTO.builder().accountId(42L).bankUserId(1L).bankCode("088")
                        .accountName("입출금통장").accountNumber("0881234567890123")
                        .status(AccountStatus.ACTIVE).balance(BigDecimal.ZERO)
                        .creationRequestHash(CreateAccountRequest.defaults().fingerprint()).build()));
        var service = new BankAccountService(accounts, users, mock(UserKeyHasher.class), mock(AccountOwnershipValidator.class));
        mvc = MockMvcBuilders.standaloneSetup(new BankAccountController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void sameKeyAndDefaultBodyReturnOriginalAccount() throws Exception {
        mvc.perform(post("/api/mock-bank/accounts").header("Idempotency-Key", key))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountId").value(42));
        verify(accounts, never()).insert(any());
    }

    @Test void differentConditionsReturn409() throws Exception {
        mvc.perform(post("/api/mock-bank/accounts").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankCode\":\"004\",\"accountName\":\"입출금통장\"}"))
                .andExpect(status().isConflict());
        verify(accounts, never()).insert(any());
    }

    @Test void missingOrMalformedKeyReturns400() throws Exception {
        mvc.perform(post("/api/mock-bank/accounts")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/mock-bank/accounts").header("Idempotency-Key", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, accounts);
    }

    @Test void unknownCreationConditionsAreNotSilentlyIgnored() throws Exception {
        mvc.perform(post("/api/mock-bank/accounts").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"balance\":100000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, accounts);
    }
}
