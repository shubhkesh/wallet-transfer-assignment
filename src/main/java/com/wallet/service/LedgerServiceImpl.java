package com.wallet.service;

import com.wallet.domain.LedgerEntry;
import com.wallet.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerServiceImpl(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public void recordTransferEntries(UUID fromWalletId, UUID toWalletId,
                                       UUID transferId, long amount) {
        LedgerEntry debitEntry = LedgerEntry.debit(fromWalletId, transferId, amount);
        LedgerEntry creditEntry = LedgerEntry.credit(toWalletId, transferId, amount);

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
    }
}
