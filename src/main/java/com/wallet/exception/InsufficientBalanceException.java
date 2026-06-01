package com.wallet.exception;

import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    private final UUID walletId;
    private final long requested;
    private final long available;

    public InsufficientBalanceException(UUID walletId, long requested, long available) {
        super(String.format("Insufficient balance in wallet %s: requested=%d, available=%d",
                walletId, requested, available));
        this.walletId = walletId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public long getRequested() {
        return requested;
    }

    public long getAvailable() {
        return available;
    }
}
