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
        when(mapper.recoverKeyState(anyLong(), nullable(String.class), eq("op"))).thenReturn(1);
        when(hasher.hash("new")).thenReturn("new-hash");
        when(hasher.hash("old")).thenReturn("old-hash");
        when(mapper.findKeyRecoveryStateForUpdate("token"))
                .thenReturn(Optional.of(new UserMapper.KeyRecoveryState(1L, active, pending, "old-hash", LocalDateTime.now().plusMinutes(5), "op", "new-hash")));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,new-hash,NULL",
            "old-hash,new-hash,old", "new-hash,NULL,old"}, nullValues = "NULL")
    void pendingConfirmedAndAlreadyRecoveredStatesAreAccepted(String active, String pending, String previous) {
        keys(active, pending);
        service.recoverUserKey("token", "new", previous, "op");
        var order = inOrder(mapper);
        order.verify(mapper).findKeyRecoveryStateForUpdate("token");
        order.verify(mapper).recoverKeyState(1L, previous == null ? null : "old-hash", "op");
    }

    @ParameterizedTest
    @CsvSource(value = {"other,NULL", "old-hash,other", "new-hash,other", "NULL,NULL"}, nullValues = "NULL")
    void conflictingStateIsNeverOverwritten(String active, String pending) {
        keys(active, pending);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old", "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), eq("op"));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "old-hash,old"}, nullValues = "NULL")
    void alreadyRecoveredReplayDoesNotWrite(String active, String previous) {
        keys(active, null);
        service.recoverUserKey("token", "new", previous, "op");
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), eq("op"));
    }

    @Test void firstKeyCanBeRecoveredWithinWindow() {
        keys("new-hash", null);
        when(mapper.findKeyRecoveryStateForUpdate("token")).thenReturn(Optional.of(
                new UserMapper.KeyRecoveryState(1L, "new-hash", null, null, LocalDateTime.now().plusMinutes(5), "op", "new-hash")));
        service.recoverUserKey("token", "new", null, "op");
        verify(mapper).recoverKeyState(1L, null, "op");
    }

    @Test void legacyConfirmedKeyCannotBeRecovered() {
        keys("new-hash", null);
        when(mapper.findKeyRecoveryStateForUpdate("token")).thenReturn(Optional.of(
                new UserMapper.KeyRecoveryState(1L, "new-hash", null, "old-hash", null, "op", "new-hash")));
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old", "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), eq("op"));
    }
    @Test void claimedPreviousKeyMustMatchBankRecordedPredecessor() {
        keys("new-hash", null);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", null, "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), eq("op"));
    }

    @Test void expiryDuringRecoveryUpdateIsRejected() {
        keys("new-hash", null);
        when(mapper.recoverKeyState(1L, "old-hash", "op")).thenReturn(0);
        when(mapper.isRecoveryExpired(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old", "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_EXPIRED);
    }

    @Test void wrongOperationCannotRecoverEvenWhenKeysMatch() {
        keys("new-hash", null);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old", "another-op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), anyString());
    }

    @Test void replayAlsoRequiresTheOriginalOperation() {
        keys("old-hash", null);
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", "old", "another-op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), anyString());
    }

    @Test void wrongCurrentKeyCannotMasqueradeAsReplay() {
        keys("old-hash", null);
        when(hasher.hash("unrelated")).thenReturn("unrelated-hash");
        assertThatThrownBy(() -> service.recoverUserKey("token", "unrelated", "old", "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), anyString());
    }
    @Test void missingUserIsNotRecovered() {
        when(hasher.hash("new")).thenReturn("hash");
        assertThatThrownBy(() -> service.recoverUserKey("missing", "new", null, "op"))
                .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), eq("op"));
    }

    @Test void sameKeyCannotBeCancelled() {
        when(hasher.hash("same")).thenReturn("hash");
        assertThatThrownBy(() -> service.recoverUserKey("token", "same", "same", "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource(value = {"old-hash,next-hash", "next-hash,NULL"}, nullValues = "NULL")
    void recoveredReceiptAllowsReplayWithoutChangingNextRotation(String active, String pending) {
        when(hasher.hash("new")).thenReturn("new-hash");
        when(hasher.hash("old")).thenReturn("old-hash");
        when(mapper.findKeyRecoveryStateForUpdate("token")).thenReturn(Optional.of(
                new UserMapper.KeyRecoveryState(1L, active, pending, null, null, "next-op", "next-hash")));
        when(mapper.findKeyOperationForUpdate(1L, "op")).thenReturn(Optional.of(
                new UserMapper.KeyOperation("new-hash", "old-hash", LocalDateTime.now(), true)));
        service.recoverUserKey("token", "new", "old", "op");
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), anyString());
        verify(mapper, never()).saveRecoveryReceipt(anyLong(), anyString(), anyString(), nullable(String.class));
    }

    @Test void recoveredReceiptStillValidatesTheRequestedPreviousKey() {
        keys("old-hash", null);
        when(mapper.findKeyOperationForUpdate(1L, "op")).thenReturn(Optional.of(
                new UserMapper.KeyOperation("new-hash", "old-hash", LocalDateTime.now(), true)));
        assertThatThrownBy(() -> service.recoverUserKey("token", "new", null, "op"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        verify(mapper, never()).recoverKeyState(anyLong(), nullable(String.class), anyString());
    }
}
