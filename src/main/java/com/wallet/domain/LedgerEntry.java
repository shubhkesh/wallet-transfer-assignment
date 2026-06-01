package com.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry debit(UUID walletId, UUID transferId, long amount) {
        return createEntry(walletId, transferId, LedgerEntryType.DEBIT, amount);
    }

    public static LedgerEntry credit(UUID walletId, UUID transferId, long amount) {
        return createEntry(walletId, transferId, LedgerEntryType.CREDIT, amount);
    }

    private static LedgerEntry createEntry(UUID walletId, UUID transferId,
                                            LedgerEntryType type, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be positive");
        }
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.walletId = walletId;
        entry.transferId = transferId;
        entry.entryType = type;
        entry.amount = amount;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public Long getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
