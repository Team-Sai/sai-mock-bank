package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.link.controller.LinkFlowController;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.dto.response.UserResponse;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.domain.user.service.UserService;
import org.teamsai.saimockbank.global.jwt.JwtTokenProvider;
import org.teamsai.saimockbank.global.security.CustomUserDetails;
import org.teamsai.saimockbank.global.util.LinkIdentityHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LinkFlowOperationTest {
    @Test
    void browserIssuanceUsesSameStateHashAsBackendCallback() throws Exception {
        var bank = mock(BankLinkService.class);
        var accounts = mock(BankAccountService.class);
        var users = mock(UserService.class);
        var controller = new LinkFlowController(bank, accounts, users, mock(JwtTokenProvider.class));
        ReflectionTestUtils.setField(controller, "linkIdentityHashSecret", "test-secret");
        var session = new MockHttpSession();
        var birth = LocalDate.of(2000, 1, 1);
        session.setAttribute("linkExpectedIdentityHash", LinkIdentityHasher.hash("name", birth, "test-secret"));
        session.setAttribute("linkState", "state");
        session.setAttribute("linkReturnUrl", "https://example.test/callback");
        when(users.getMyInfo(1L)).thenReturn(UserResponse.builder().name("name").birthDate(birth).build());
        when(accounts.getMyAccounts(1L)).thenReturn(List.of(
                new AccountListResponse(1L, null, null, null, null, null, null, null)));
        String operationId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("state".getBytes(StandardCharsets.UTF_8)));
        when(bank.issueUserKey("name", "token", operationId))
                .thenReturn(new MockBankLinkResponse("new-key", LocalDateTime.now()));

        var response = controller.confirmLink(new CustomUserDetails(UserDTO.builder()
                        .bankUserId(1L).name("name").userToken("token").build()),
                new LinkFlowController.LinkConfirmRequest(List.of(1L)), session);

        verify(bank).issueUserKey("name", "token", operationId);
        assertThat(response.redirectUrl()).contains("state=state", "userKey=new-key");
        assertThat(session.getAttribute("linkState")).isNull();
    }
}
