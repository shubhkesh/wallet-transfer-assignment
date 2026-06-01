package com.wallet.controller.dto;

import com.wallet.domain.Transfer;
import com.wallet.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferHistoryResponse(
        UUID transferId,
        UUID fromWalletId,
        UUID toWalletId,
        long amount,
        TransferStatus status,
        Instant createdAt
) {

    public static TransferHistoryResponse from(Transfer transfer) {
        return new TransferHistoryResponse(
                transfer.getId(),
                transfer.getFromWalletId(),
                transfer.getToWalletId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt()
        );
    }
}
