package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.teamsai.saimockbank.domain.link.controller.BankLinkCallbackController;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.global.config.SecurityConfig;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BankLinkCallbackController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "link-callback.api-key=test-only-api-key")
class BankLinkCallbackControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private BankLinkService bankLinkService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${link-callback.api-key}")
    private String validApiKey;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void confirmKey_헤더없으면_401() throws Exception {
        mockMvc.perform(post("/api/link/confirm-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmKey_잘못된키면_401() throws Exception {
        mockMvc.perform(post("/api/link/confirm-key")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmKey_올바른키면_200() throws Exception {
        mockMvc.perform(post("/api/link/confirm-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isOk());

        verify(bankLinkService).confirmUserKey("rawKey");
    }

    @Test
    void confirmKey_userKey가_빈값이면_400() throws Exception {
        mockMvc.perform(post("/api/link/confirm-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmKey_userKey가_null이면_400() throws Exception {
        mockMvc.perform(post("/api/link/confirm-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeKey_헤더없으면_401() throws Exception {
        mockMvc.perform(post("/api/link/revoke-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeKey_잘못된키면_401() throws Exception {
        mockMvc.perform(post("/api/link/revoke-key")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeKey_올바른키면_200() throws Exception {
        mockMvc.perform(post("/api/link/revoke-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"rawKey\"}"))
                .andExpect(status().isOk());

        verify(bankLinkService).revokeUserKey("rawKey");
    }

    @Test
    void revokeKey_userKey가_빈값이면_400() throws Exception {
        mockMvc.perform(post("/api/link/revoke-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeKey_userKey가_null이면_400() throws Exception {
        mockMvc.perform(post("/api/link/revoke-key")
                        .header("X-Internal-Api-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}