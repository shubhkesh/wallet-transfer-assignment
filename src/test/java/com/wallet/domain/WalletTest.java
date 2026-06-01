package com.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletTest {

    @Test
    void createWalletSuccessfully() {
        UUID id = UUID.randomUUID();
        Wallet wallet = Wallet.create(id, 1000L);

        assertEquals(id, wallet.getId());
        assertEquals(1000L, wallet.getBalance());
        assertNotNull(wallet.getCreatedAt());
    }

    @Test
    void createWalletRejectsNegativeBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> Wallet.create(UUID.randomUUID(), -100L));
    }

    @Test
    void createWalletWithZeroBalance() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 0L);
        assertEquals(0L, wallet.getBalance());
    }

    @Test
    void debitReducesBalance() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 1000L);
        wallet.debit(300);

        assertEquals(700L, wallet.getBalance());
    }

    @Test
    void debitEntireBalance() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 500L);
        wallet.debit(500);

        assertEquals(0L, wallet.getBalance());
    }

    @Test
    void debitRejectsInsufficientBalance() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 100L);

        assertThrows(IllegalStateException.class, () -> wallet.debit(200));
    }

    @Test
    void debitRejectsZeroAmount() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 100L);

        assertThrows(IllegalArgumentException.class, () -> wallet.debit(0));
    }

    @Test
    void debitRejectsNegativeAmount() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 100L);

        assertThrows(IllegalArgumentException.class, () -> wallet.debit(-50));
    }

    @Test
    void creditIncreasesBalance() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 1000L);
        wallet.credit(500);

        assertEquals(1500L, wallet.getBalance());
    }

    @Test
    void creditRejectsZeroAmount() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 100L);

        assertThrows(IllegalArgumentException.class, () -> wallet.credit(0));
    }

    @Test
    void creditRejectsNegativeAmount() {
        Wallet wallet = Wallet.create(UUID.randomUUID(), 100L);

        assertThrows(IllegalArgumentException.class, () -> wallet.credit(-50));
    }
}
