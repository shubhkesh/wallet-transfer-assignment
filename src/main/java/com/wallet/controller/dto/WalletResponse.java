package com.wallet.controller.dto;

import com.wallet.domain.Wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        long balance,
        Instant createdAt,
        Instant updatedAt
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}
