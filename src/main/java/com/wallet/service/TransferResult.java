package com.wallet.service;

import com.wallet.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResult(
        UUID transferId,
        UUID fromWalletId,
        UUID toWalletId,
        long amount,
        TransferStatus status,
        Instant createdAt,
        boolean idempotentReplay
) {
}
