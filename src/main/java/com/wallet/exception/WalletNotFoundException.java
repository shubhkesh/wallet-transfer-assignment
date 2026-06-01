package com.wallet.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    private final UUID walletId;

    public WalletNotFoundException(UUID walletId) {
        super(String.format("Wallet not found: %s", walletId));
        this.walletId = walletId;
    }

    public UUID getWalletId() {
        return walletId;
    }
}
