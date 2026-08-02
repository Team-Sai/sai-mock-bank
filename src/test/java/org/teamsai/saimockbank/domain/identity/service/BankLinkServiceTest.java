package org.teamsai.saimockbank.domain.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;
import org.teamsai.saimockbank.domain.identity.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.identity.mapper.IdentityMapper;
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
    private IdentityMapper identityMapper;

    @Mock
    private UserKeyHasher userKeyHasher;

    @InjectMocks
    private BankLinkService bankLinkService;

    private static final String NAME = "홍길동";
    private static final String USER_TOKEN = "user-token-abc";
    private static final Long IDENTITY_ID = 1L;
    private static final String HASHED_VALUE = "hashed-fixed-value";

    private IdentityDTO createIdentity(Long identityId, String userKeyHash) {
        IdentityDTO identity = new IdentityDTO();
        ReflectionTestUtils.setField(identity, "identityId", identityId);
        ReflectionTestUtils.setField(identity, "name", NAME);
        ReflectionTestUtils.setField(identity, "userKeyHash", userKeyHash);
        return identity;
    }

    @Test
    @DisplayName("연동되지 않은 회원이면 userKey를 발급하고 원본 값을 응답으로 반환한다")
    void issuesUserKeyWhenNotLinkedYet() {
        // given
        IdentityDTO identity = createIdentity(IDENTITY_ID, null);
        given(identityMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(identity));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);

        MockBankLinkResponse response = bankLinkService.issueUserKey(NAME, USER_TOKEN);

        assertThat(response.userKey()).isNotNull().startsWith("mb_");
        assertThat(response.issuedAt()).isNotNull();

        ArgumentCaptor<String> hashInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(userKeyHasher).hash(hashInputCaptor.capture());
        assertThat(hashInputCaptor.getValue()).isEqualTo(response.userKey());

        ArgumentCaptor<String> userKeyHashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> issuedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(identityMapper).updateUserKey(
                eq(IDENTITY_ID),
                userKeyHashCaptor.capture(),
                issuedAtCaptor.capture()
        );

        assertThat(userKeyHashCaptor.getValue()).isEqualTo(HASHED_VALUE);
        assertThat(issuedAtCaptor.getValue()).isEqualTo(response.issuedAt());
    }

    @Test
    @DisplayName("호출할 때마다 서로 다른 userKey를 발급한다")
    void generatesDifferentUserKeyOnEachCall() {
        IdentityDTO identity1 = createIdentity(IDENTITY_ID, null);
        IdentityDTO identity2 = createIdentity(2L, null);
        given(identityMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(identity1))
                .willReturn(Optional.of(identity2));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);

        MockBankLinkResponse first = bankLinkService.issueUserKey(NAME, USER_TOKEN);
        MockBankLinkResponse second = bankLinkService.issueUserKey(NAME, USER_TOKEN);

        assertThat(first.userKey()).isNotEqualTo(second.userKey());
    }

    @Test
    @DisplayName("일치하는 회원이 없으면 CORRECT_USER_NOT_FOUND 예외가 발생하고 해싱/업데이트는 호출되지 않는다")
    void throwsWhenUserNotFound() {
        given(identityMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.CORRECT_USER_NOT_FOUND);

        verify(userKeyHasher, never()).hash(anyString());
        verify(identityMapper, never()).updateUserKey(any(), any(), any());
    }

    @Test
    @DisplayName("이미 userKeyHash가 발급된 회원이면 ALREADY_LINKED_USER 예외가 발생하고 해싱/업데이트는 호출되지 않는다")
    void throwsWhenAlreadyLinked() {
        IdentityDTO alreadyLinkedIdentity = createIdentity(IDENTITY_ID, "existing-hashed-user-key");
        given(identityMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(alreadyLinkedIdentity));

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.ALREADY_LINKED_USER);

        verify(userKeyHasher, never()).hash(anyString());
        verify(identityMapper, never()).updateUserKey(any(), any(), any());
    }
}
