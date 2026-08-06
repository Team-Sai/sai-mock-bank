package org.teamsai.saimockbank.domain.account.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class AccountOwnershipValidator {

    private final UserKeyHasher userKeyHasher;

    public void verify(String ownerHash, String rawUserKey, Supplier<DomainException> exceptionSupplier) {
        String hashedKey = userKeyHasher.hash(rawUserKey);
        if (ownerHash == null || !ownerHash.equals(hashedKey)) {
            throw exceptionSupplier.get();
        }
    }
}
