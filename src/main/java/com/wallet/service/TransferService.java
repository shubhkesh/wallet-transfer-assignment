package com.wallet.service;

public interface TransferService {

    TransferResult executeTransfer(String idempotencyKey, String fromWalletId,
                                    String toWalletId, long amount);
}
