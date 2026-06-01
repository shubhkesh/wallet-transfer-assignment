package com.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerEntryTest {

    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID TRANSFER_ID = UUID.randomUUID();

    @Test
    void createDebitEntry() {
        LedgerEntry entry = LedgerEntry.debit(WALLET_ID, TRANSFER_ID, 500);

        assertNotNull(entry.getId());
        assertEquals(WALLET_ID, entry.getWalletId());
        assertEquals(TRANSFER_ID, entry.getTransferId());
        assertEquals(LedgerEntryType.DEBIT, entry.getEntryType());
        assertEquals(500L, entry.getAmount());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void createCreditEntry() {
        LedgerEntry entry = LedgerEntry.credit(WALLET_ID, TRANSFER_ID, 300);

        assertEquals(LedgerEntryType.CREDIT, entry.getEntryType());
        assertEquals(300L, entry.getAmount());
    }

    @Test
    void debitRejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.debit(WALLET_ID, TRANSFER_ID, 0));
    }

    @Test
    void debitRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.debit(WALLET_ID, TRANSFER_ID, -100));
    }

    @Test
    void creditRejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.credit(WALLET_ID, TRANSFER_ID, 0));
    }

    @Test
    void creditRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.credit(WALLET_ID, TRANSFER_ID, -100));
    }
}
