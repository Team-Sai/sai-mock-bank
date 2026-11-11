package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.teamsai.saimockbank.domain.account.controller.BankAccountController;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.dto.MyAccountResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.global.config.SecurityConfig;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;
import org.teamsai.saimockbank.global.jwt.JwtTokenProvider;
import org.teamsai.saimockbank.global.security.InternalApiKeyFilter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BankAccountController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        InternalApiKeyFilter.class
})
@TestPropertySource(properties = "link-callback.api-key=test-only-api-key")
class BankAccountSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BankAccountService bankAccountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserMapper userMapper;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void accountListWithUserKeyDoesNotRequireJwt() throws Exception {
        when(bankAccountService.getAccounts("valid-user-key"))
                .thenReturn(List.of());

        mvc.perform(get("/api/mock-bank/accounts")
                        .header("X-User-Key", "valid-user-key"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(bankAccountService).getAccounts("valid-user-key");
    }

    @Test
    void accountCreationWithOnlyUserKeyIsUnauthorized() throws Exception {
        mvc.perform(post("/api/mock-bank/accounts")
                        .header("X-User-Key", "valid-user-key")
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bankAccountService);
    }

    @Test
    void accountCreationWithJwtReachesService() throws Exception {
        UUID requestKey = UUID.randomUUID();
        var user = UserDTO.builder()
                .bankUserId(1L)
                .name("테스트")
                .build();

        when(jwtTokenProvider.getUserIdIfValid("valid-token"))
                .thenReturn(Optional.of(1L));
        when(userMapper.findById(1L))
                .thenReturn(Optional.of(user));

        mvc.perform(post("/api/mock-bank/accounts")
                        .header("Authorization", "Bearer valid-token")
                        .header("Idempotency-Key", requestKey))
                .andExpect(status().isCreated());

        verify(bankAccountService).postAccount(1L, requestKey, null);
    }

    @Test
    void myAccountsWithOnlyUserKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/api/mock-bank/accounts/my")
                        .header("X-User-Key", "valid-user-key"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bankAccountService);
    }

    @Test
    void authenticatedMyAccountsResponseIncludesFullAccountNumber() throws Exception {
        when(jwtTokenProvider.getUserIdIfValid("valid-token")).thenReturn(Optional.of(1L));
        when(userMapper.findById(1L)).thenReturn(Optional.of(UserDTO.builder().bankUserId(1L).build()));
        var account = BankAccountDTO.builder()
                .accountId(42L).bankUserId(1L).bankCode("088").bankName("신한은행")
                .accountName("입출금통장").accountHolderName("테스트")
                .accountNumber("1101234560915").balance(new BigDecimal("54321.00"))
                .status(AccountStatus.ACTIVE).build();
        when(bankAccountService.getMyAccountsWithNumbers(1L)).thenReturn(List.of(MyAccountResponse.from(account)));

        mvc.perform(get("/api/mock-bank/accounts/my").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("1101234560915"))
                .andExpect(jsonPath("$[0].maskedAccountNumber").exists())
                .andExpect(jsonPath("$[0].balance").value(54321));
        verify(bankAccountService).getMyAccountsWithNumbers(1L);
    }
}
