package com.wallet.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TransferRequest(

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey,

        @NotBlank(message = "fromWalletId is required")
        String fromWalletId,

        @NotBlank(message = "toWalletId is required")
        String toWalletId,

        @Positive(message = "amount must be positive")
        long amount
) {
}
