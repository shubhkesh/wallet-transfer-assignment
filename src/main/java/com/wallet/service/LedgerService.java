package com.wallet.service;

import java.util.UUID;

public interface LedgerService {

    void recordTransferEntries(UUID fromWalletId, UUID toWalletId,
                                UUID transferId, long amount);
}
