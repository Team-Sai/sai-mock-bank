package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.user.exception.UserErrorCode;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;

import java.util.Optional;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankKeyRecoveryServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final UserKeyHasher hasher = mock(UserKeyHasher.class);
    private final BankLinkService service = new BankLinkService(mapper, hasher);

    private void keys(String active, String pending) {
        when(mapper.recoverKeyState(anyLong(), nullable(String.class))).thenReturn(1);
        when(hasher.hash("new")).thenReturn("new-hash");
        when(hasher.hash("old")).thenReturn("old-hash");
        when(mapper.findKeyRecoveryStateForUpdate("token"))
                .thenReturn(Optional.of(new UserMapper.KeyRecoveryState(1L, active, pending, "old-hash", LocalDateTime.now().plusMinutes(5))));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,new-hash,NULL",
            "old-hash,new-hash,old", "new-hash,NULL,old"}, nullValues = "NULL")
    void pendingConfirmedAndAlreadyRecoveredStatesAreAccepted(String active, String pending, String previous) {
        keys(active, pending);
        service.recoverUserKey("token", "new", previous);
        var order = inOrder(mapper);
        order.verify(mapper).findKeyRecoveryStateForUpdate("token");
        order.verify(mapper).recoverKeyState(1L, previous == null ? null : "old-hash");
    }

    @ParameterizedTest
    @CsvSource(value = {"other,NULL", "old-hash,other", "new-hash,other", "NULL,NULL"}, nullValues = "NULL")
    void conflictingStateIsNeverOverwritten(String active, String pending) {
        keys(active, pending);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "old-hash,old"}, nullValues = "NULL")
    void alreadyRecoveredReplayDoesNotWrite(String active, String previous) {
        keys(active, null);
        service.recoverUserKey("token", "new", previous);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class));
    }

    @Test void firstKeyCanBeRecoveredWithinWindow() {
        keys("new-hash", null);
        when(mapper.findKeyRecoveryStateForUpdate("token")).thenReturn(Optional.of(
                new UserMapper.KeyRecoveryState(1L, "new-hash", null, null, LocalDateTime.now().plusMinutes(5))));
        service.recoverUserKey("token", "new", null);
        verify(mapper).recoverKeyState(1L, null);
    }

    @Test void legacyConfirmedKeyCannotBeRecovered() {
        keys("new-hash", null);
        when(mapper.findKeyRecoveryStateForUpdate("token")).thenReturn(Optional.of(
                new UserMapper.KeyRecoveryState(1L, "new-hash", null, "old-hash", null)));
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class));
    }
    @Test void claimedPreviousKeyMustMatchBankRecordedPredecessor() {
        keys("new-hash", null);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", null))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class));
    }

    @Test void expiryDuringRecoveryUpdateIsRejected() {
        keys("new-hash", null);
        when(mapper.recoverKeyState(1L, "old-hash")).thenReturn(0);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
    }
    @Test void missingUserIsNotRecovered() {
        when(hasher.hash("new")).thenReturn("hash");
        assertThatThrownBy(() -> service.recoverUserKey("missing", "new", null))
                .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class));
    }

    @Test void sameKeyCannotBeCancelled() {
        when(hasher.hash("same")).thenReturn("hash");
        assertThatThrownBy(() -> service.recoverUserKey("token", "same", "same"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verifyNoInteractions(mapper);
    }
}
