package com.wallet.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferStatusTest {

    @Test
    void pendingCanTransitionToProcessed() {
        assertTrue(TransferStatus.PENDING.canTransitionTo(TransferStatus.PROCESSED));
    }

    @Test
    void pendingCanTransitionToFailed() {
        assertTrue(TransferStatus.PENDING.canTransitionTo(TransferStatus.FAILED));
    }

    @Test
    void processedCannotTransitionToAnyState() {
        assertFalse(TransferStatus.PROCESSED.canTransitionTo(TransferStatus.PENDING));
        assertFalse(TransferStatus.PROCESSED.canTransitionTo(TransferStatus.FAILED));
        assertFalse(TransferStatus.PROCESSED.canTransitionTo(TransferStatus.PROCESSED));
    }

    @Test
    void failedCannotTransitionToAnyState() {
        assertFalse(TransferStatus.FAILED.canTransitionTo(TransferStatus.PENDING));
        assertFalse(TransferStatus.FAILED.canTransitionTo(TransferStatus.PROCESSED));
        assertFalse(TransferStatus.FAILED.canTransitionTo(TransferStatus.FAILED));
    }

    @Test
    void pendingCannotTransitionToPending() {
        assertFalse(TransferStatus.PENDING.canTransitionTo(TransferStatus.PENDING));
    }
}
