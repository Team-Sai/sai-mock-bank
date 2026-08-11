package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.exception.UserErrorCode;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankLinkService 단위 테스트")
class BankLinkServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserKeyHasher userKeyHasher;

    @InjectMocks
    private BankLinkService bankLinkService;

    private static final String NAME = "홍길동";
    private static final String USER_TOKEN = "user-token-abc";
    private static final Long BANK_USER_ID = 1L;
    private static final String HASHED_VALUE = "hashed-fixed-value";

    private UserDTO createIdentity(Long userId, String userKeyHash) {
        UserDTO user = new UserDTO();
        ReflectionTestUtils.setField(user, "bankUserId", userId);
        ReflectionTestUtils.setField(user, "name", NAME);
        ReflectionTestUtils.setField(user, "userKeyHash", userKeyHash);
        return user;
    }

    @Test
    @DisplayName("연동되지 않은 회원이면 userKey를 발급하고 원본 값을 응답으로 반환한다")
    void issuesUserKeyWhenNotLinkedYet() {
        UserDTO identity = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(identity));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.updateUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class)))
                .willReturn(1);

        MockBankLinkResponse response = bankLinkService.issueUserKey(NAME, USER_TOKEN);

        assertThat(response.userKey()).isNotNull().startsWith("sai_");
        assertThat(response.issuedAt()).isNotNull();

        ArgumentCaptor<String> hashInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(userKeyHasher).hash(hashInputCaptor.capture());
        assertThat(hashInputCaptor.getValue()).isEqualTo(response.userKey());

        ArgumentCaptor<String> userKeyHashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> issuedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userMapper).updateUserKey(
                eq(BANK_USER_ID),
                userKeyHashCaptor.capture(),
                issuedAtCaptor.capture()
        );

        assertThat(userKeyHashCaptor.getValue()).isEqualTo(HASHED_VALUE);
        assertThat(issuedAtCaptor.getValue()).isEqualTo(response.issuedAt());
    }

    @Test
    @DisplayName("서로 다른 사용자에게는 서로 다른 userKey를 발급한다")
    void generatesDifferentUserKeyForDifferentUsers() {
        String name2 = "김철수";
        String userToken2 = "user-token-def";

        UserDTO identity1 = createIdentity(BANK_USER_ID, null);
        UserDTO identity2 = createIdentity(2L, null);

        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(identity1));
        given(userMapper.findByNameAndUserToken(name2, userToken2))
                .willReturn(Optional.of(identity2));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.updateUserKey(anyLong(), anyString(), any(LocalDateTime.class)))
                .willReturn(1);

        MockBankLinkResponse first = bankLinkService.issueUserKey(NAME, USER_TOKEN);
        MockBankLinkResponse second = bankLinkService.issueUserKey(name2, userToken2);

        assertThat(first.userKey()).isNotEqualTo(second.userKey());
    }

    @Test
    @DisplayName("일치하는 회원이 없으면 USER_NOT_FOUND 예외가 발생하고 해싱/업데이트는 호출되지 않는다")
    void throwsWhenUserNotFound() {
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(userKeyHasher, never()).hash(anyString());
        verify(userMapper, never()).updateUserKey(any(), any(), any());
    }

    @Test
    @DisplayName("이미 userKeyHash가 발급된 회원도 재연동 시 새 userKey를 발급받는다")
    void reissuesUserKeyWhenAlreadyLinked() {
        UserDTO alreadyLinkedUser = createIdentity(BANK_USER_ID, "existing-hashed-user-key");
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(alreadyLinkedUser));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.updateUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class)))
                .willReturn(1);

        MockBankLinkResponse response = bankLinkService.issueUserKey(NAME, USER_TOKEN);

        assertThat(response.userKey()).isNotNull().startsWith("sai_");
        verify(userKeyHasher).hash(anyString());
        verify(userMapper).updateUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("동시 요청으로 인해 업데이트가 반영되지 않으면 LINK_KEY_UPDATE_CONFLICT 예외가 발생한다")
    void throwsWhenConcurrentUpdateFails() {
        UserDTO user = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(user));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.updateUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class)))
                .willReturn(0);

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.LINK_KEY_UPDATE_CONFLICT);  // UserErrorCode → IdentityErrorCode로 변경
    }
}
