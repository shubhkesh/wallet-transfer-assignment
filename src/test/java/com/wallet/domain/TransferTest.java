package com.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferTest {

    private static final UUID WALLET_A = UUID.randomUUID();
    private static final UUID WALLET_B = UUID.randomUUID();

    @Test
    void createTransferSuccessfully() {
        Transfer transfer = Transfer.create(WALLET_A, WALLET_B, 500);

        assertNotNull(transfer.getId());
        assertEquals(WALLET_A, transfer.getFromWalletId());
        assertEquals(WALLET_B, transfer.getToWalletId());
        assertEquals(500L, transfer.getAmount());
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
        assertNotNull(transfer.getCreatedAt());
    }

    @Test
    void createTransferRejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Transfer.create(WALLET_A, WALLET_B, 0));
    }

    @Test
    void createTransferRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Transfer.create(WALLET_A, WALLET_B, -100));
    }

    @Test
    void createTransferRejectsSameWallet() {
        assertThrows(IllegalArgumentException.class,
                () -> Transfer.create(WALLET_A, WALLET_A, 100));
    }

    @Test
    void transitionFromPendingToProcessed() {
        Transfer transfer = Transfer.create(WALLET_A, WALLET_B, 100);
        transfer.transitionTo(TransferStatus.PROCESSED);

        assertEquals(TransferStatus.PROCESSED, transfer.getStatus());
    }

    @Test
    void transitionFromPendingToFailed() {
        Transfer transfer = Transfer.create(WALLET_A, WALLET_B, 100);
        transfer.transitionTo(TransferStatus.FAILED);

        assertEquals(TransferStatus.FAILED, transfer.getStatus());
    }

    @Test
    void transitionFromProcessedThrows() {
        Transfer transfer = Transfer.create(WALLET_A, WALLET_B, 100);
        transfer.transitionTo(TransferStatus.PROCESSED);

        assertThrows(IllegalStateException.class,
                () -> transfer.transitionTo(TransferStatus.FAILED));
    }

    @Test
    void transitionFromFailedThrows() {
        Transfer transfer = Transfer.create(WALLET_A, WALLET_B, 100);
        transfer.transitionTo(TransferStatus.FAILED);

        assertThrows(IllegalStateException.class,
                () -> transfer.transitionTo(TransferStatus.PROCESSED));
    }
}
