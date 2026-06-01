package com.wallet.controller.dto;

import com.wallet.domain.TransferStatus;
import com.wallet.service.TransferResult;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        UUID fromWalletId,
        UUID toWalletId,
        long amount,
        TransferStatus status,
        Instant createdAt
) {

    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.transferId(),
                result.fromWalletId(),
                result.toWalletId(),
                result.amount(),
                result.status(),
                result.createdAt()
        );
    }
}
