package org.teamsai.saimockbank.domain.user;

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
import org.teamsai.saimockbank.domain.user.controller.UserController;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.dto.response.UserResponse;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.UserService;
import org.teamsai.saimockbank.global.config.SecurityConfig;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;
import org.teamsai.saimockbank.global.jwt.JwtTokenProvider;
import org.teamsai.saimockbank.global.security.InternalApiKeyFilter;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, InternalApiKeyFilter.class })
@TestPropertySource(properties = "link-callback.api-key=test-only-api-key")
class MyPageSecurityTest {
    @Autowired private WebApplicationContext context;
    @MockitoBean private UserService userService;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void pageRendersWithoutBearerHeaderSoBrowserNavigationCanLoadItsAuthenticatedScript() throws Exception {
        mvc.perform(get("/mypage"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/mypage"))
                .andExpect(content().string(containsString("id=\"account-list\"")))
                .andExpect(content().string(containsString("id=\"createAccountDialog\"")))
                .andExpect(content().string(not(containsString("fdsNotificationButton"))))
                .andExpect(content().string(not(containsString("상세 정보"))))
                .andExpect(content().string(containsString("보유 계좌")));
        verifyNoInteractions(userService);
    }

    @Test
    void personalInformationStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void authenticatedProfileIncludesJoinDate() throws Exception {
        UserDTO user = UserDTO.builder().bankUserId(42L).name("검증 사용자").userToken("SAI-TEST")
                .createdAt(LocalDateTime.of(2026, 11, 9, 12, 0)).build();
        when(jwtTokenProvider.getUserIdIfValid("valid-token")).thenReturn(Optional.of(42L));
        when(userMapper.findById(42L)).thenReturn(Optional.of(user));
        when(userService.getMyInfo(42L)).thenReturn(UserResponse.from(user));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("userToken").value("SAI-TEST"))
                .andExpect(jsonPath("createdAt").value("2026-11-09T12:00:00"));
    }
}
