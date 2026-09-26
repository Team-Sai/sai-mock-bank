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
        given(userMapper.findByIdForUpdate(userId)).willReturn(Optional.of(user));
        given(userKeyHasher.deriveUserKey(userId, "op")).willReturn("mb_derived-" + userId);
        return user;
    }

    @Test
    @DisplayName("연동되지 않은 회원이면 PENDING 상태로 userKey를 저장하고 원본 값을 응답으로 반환한다")
    void issuesPendingUserKeyWhenNotLinkedYet() {
        UserDTO identity = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(identity));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.savePendingUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), eq("op"))).willReturn(1);

        MockBankLinkResponse response = bankLinkService.issueUserKey(NAME, USER_TOKEN, "op");

        assertThat(response.userKey()).isNotNull().startsWith("mb_");
        assertThat(response.issuedAt()).isNotNull();

        ArgumentCaptor<String> hashInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(userKeyHasher).hash(hashInputCaptor.capture());
        assertThat(hashInputCaptor.getValue()).isEqualTo(response.userKey());

        ArgumentCaptor<String> pendingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> issuedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> expiresAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userMapper).savePendingUserKey(eq(BANK_USER_ID), pendingKeyCaptor.capture(), issuedAtCaptor.capture(), expiresAtCaptor.capture(), eq("op"));
        assertThat(pendingKeyCaptor.getValue()).isEqualTo(HASHED_VALUE);
        assertThat(issuedAtCaptor.getValue()).isEqualTo(response.issuedAt());
        assertThat(expiresAtCaptor.getValue()).isAfter(issuedAtCaptor.getValue());
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
        given(userMapper.savePendingUserKey(anyLong(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), eq("op"))).willReturn(1);

        MockBankLinkResponse first = bankLinkService.issueUserKey(NAME, USER_TOKEN, "op");
        MockBankLinkResponse second = bankLinkService.issueUserKey(name2, userToken2, "op");

        assertThat(first.userKey()).isNotEqualTo(second.userKey());
    }

    @Test
    @DisplayName("일치하는 회원이 없으면 USER_NOT_FOUND 예외가 발생하고 해싱/저장은 호출되지 않는다")
    void throwsWhenUserNotFound() {
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN, "op"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(userKeyHasher, never()).hash(anyString());
        verify(userMapper, never()).savePendingUserKey(any(), any(), any(), any(), eq("op"));
    }

    @Test
    @DisplayName("이미 연동된 회원도 재연동 시 새 PENDING userKey를 발급받는다")
    void reissuesPendingUserKeyWhenAlreadyLinked() {
        UserDTO alreadyLinkedUser = createIdentity(BANK_USER_ID, "existing-hashed-user-key");
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(alreadyLinkedUser));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.savePendingUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), eq("op"))).willReturn(1);

        MockBankLinkResponse response = bankLinkService.issueUserKey(NAME, USER_TOKEN, "op");

        assertThat(response.userKey()).isNotNull().startsWith("mb_");
        verify(userKeyHasher).hash(anyString());
        verify(userMapper).savePendingUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), eq("op"));
    }

    @Test
    @DisplayName("이미 유효한 PENDING이 진행 중이면 PENDING_KEY_ALREADY_EXISTS 예외가 발생한다")
    void throwsWhenPendingAlreadyExists() {
        UserDTO user = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN))
                .willReturn(Optional.of(user));
        given(userKeyHasher.hash(anyString())).willReturn(HASHED_VALUE);
        given(userMapper.savePendingUserKey(eq(BANK_USER_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), eq("op"))).willReturn(0);

        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN, "op"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.PENDING_KEY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("유효한 PENDING의 rawKey로 confirm하면 ACTIVE로 전환된다")
    void confirmsUserKeySuccessfully() {
        given(userKeyHasher.hash("raw-key")).willReturn(HASHED_VALUE);
        given(userMapper.promotePendingToActive(HASHED_VALUE, "op")).willReturn(1);

        bankLinkService.confirmUserKey("raw-key", "op");

        verify(userMapper).promotePendingToActive(HASHED_VALUE, "op");
    }

    @Test
    @DisplayName("대응하는 PENDING이 없으면 PENDING_KEY_NOT_FOUND 예외가 발생한다")
    void throwsWhenPendingKeyNotFoundOnConfirm() {
        given(userKeyHasher.hash("raw-key")).willReturn(HASHED_VALUE);
        given(userMapper.promotePendingToActive(HASHED_VALUE, "op")).willReturn(0);

        assertThatThrownBy(() -> bankLinkService.confirmUserKey("raw-key", "op"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.PENDING_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 상태의 rawKey로 revoke하면 EXPIRED로 전환된다")
    void revokesActiveKeySuccessfully() {
        given(userKeyHasher.hash("raw-key")).willReturn(HASHED_VALUE);
        given(userMapper.revokeUserKey(HASHED_VALUE)).willReturn(1);

        bankLinkService.revokeUserKey("raw-key");

        verify(userMapper).revokeUserKey(HASHED_VALUE);
    }

    @Test
    @DisplayName("대응하는 ACTIVE 키가 없으면 ACTIVE_KEY_NOT_FOUND 예외가 발생한다")
    void throwsWhenActiveKeyNotFoundOnRevoke() {
        given(userKeyHasher.hash("raw-key")).willReturn(HASHED_VALUE);
        given(userMapper.revokeUserKey(HASHED_VALUE)).willReturn(0);

        assertThatThrownBy(() -> bankLinkService.revokeUserKey("raw-key"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.ACTIVE_KEY_NOT_FOUND);
    }

    @Test
    void repeatedIssuanceReturnsOriginalKeyAndTimestampWithoutRenewingPending() {
        var user = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN)).willReturn(Optional.of(user));
        given(userKeyHasher.hash("mb_derived-1")).willReturn(HASHED_VALUE);
        var originalTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        given(userMapper.findKeyOperationForUpdate(BANK_USER_ID, "op"))
                .willReturn(Optional.of(new UserMapper.KeyOperation(HASHED_VALUE, null, originalTime, false)));

        var result = bankLinkService.issueUserKey(NAME, USER_TOKEN, "op");

        assertThat(result.userKey()).isEqualTo("mb_derived-1");
        assertThat(result.issuedAt()).isEqualTo(originalTime);
        verify(userMapper, never()).savePendingUserKey(any(), any(), any(), any(), any());
        verify(userMapper, never()).insertKeyOperation(any(), any(), any(), any(), any());
    }

    @Test
    void recoveredOperationCannotIssueAnotherKey() {
        var user = createIdentity(BANK_USER_ID, null);
        given(userMapper.findByNameAndUserToken(NAME, USER_TOKEN)).willReturn(Optional.of(user));
        given(userKeyHasher.hash("mb_derived-1")).willReturn(HASHED_VALUE);
        given(userMapper.findKeyOperationForUpdate(BANK_USER_ID, "op"))
                .willReturn(Optional.of(new UserMapper.KeyOperation(HASHED_VALUE, null, LocalDateTime.now(), true)));
        assertThatThrownBy(() -> bankLinkService.issueUserKey(NAME, USER_TOKEN, "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(userMapper, never()).savePendingUserKey(any(), any(), any(), any(), any());
    }
}
