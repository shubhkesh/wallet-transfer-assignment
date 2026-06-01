package com.wallet.domain;

import java.util.Map;
import java.util.Set;

public enum TransferStatus {

    PENDING,
    PROCESSED,
    FAILED;

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, Set.of(PROCESSED, FAILED),
            PROCESSED, Set.of(),
            FAILED, Set.of()
    );

    public boolean canTransitionTo(TransferStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
