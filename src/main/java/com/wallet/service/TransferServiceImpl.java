package com.wallet.service;

import com.wallet.domain.IdempotencyRecord;
import com.wallet.domain.Transfer;
import com.wallet.domain.TransferStatus;
import com.wallet.domain.Wallet;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidTransferException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransferRepository;
import com.wallet.repository.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(WalletRepository walletRepository,
                                TransferRepository transferRepository,
                                LedgerService ledgerService,
                                IdempotencyService idempotencyService,
                                ObjectMapper objectMapper) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TransferResult executeTransfer(String idempotencyKey, String fromWalletId,
                                           String toWalletId, long amount) {
        log.debug("Processing transfer: idempotencyKey={}, from={}, to={}, amount={}",
                idempotencyKey, fromWalletId, toWalletId, amount);

        // 1. Check idempotency — return cached result if exists
        Optional<IdempotencyRecord> existingRecord = idempotencyService.findByKey(idempotencyKey);
        if (existingRecord.isPresent()) {
            log.info("Idempotent replay for key={}", idempotencyKey);
            return deserializeResult(existingRecord.get(), true);
        }

        // 2. Parse and validate wallet IDs
        UUID fromId = parseWalletId(fromWalletId);
        UUID toId = parseWalletId(toWalletId);

        if (fromId.equals(toId)) {
            throw new InvalidTransferException("Cannot transfer to the same wallet");
        }

        // 3. Lock wallets in deterministic order to prevent deadlocks
        UUID firstLock = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondLock = fromId.compareTo(toId) < 0 ? toId : fromId;

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstLock)
                .orElseThrow(() -> new WalletNotFoundException(firstLock));
        Wallet secondWallet = walletRepository.findByIdForUpdate(secondLock)
                .orElseThrow(() -> new WalletNotFoundException(secondLock));

        // 4. Resolve source and destination from the locked wallets
        Wallet sourceWallet = firstLock.equals(fromId) ? firstWallet : secondWallet;
        Wallet destWallet = firstLock.equals(fromId) ? secondWallet : firstWallet;

        // 5. Create transfer in PENDING state
        Transfer transfer = Transfer.create(fromId, toId, amount);
        transferRepository.save(transfer);

        // 6. Check balance and execute
        if (sourceWallet.getBalance() < amount) {
            transfer.transitionTo(TransferStatus.FAILED);
            transferRepository.save(transfer);

            TransferResult failedResult = toResult(transfer, false);
            idempotencyService.saveRecord(idempotencyKey, transfer.getId(),
                    HttpStatus.UNPROCESSABLE_ENTITY.value(), serializeResult(failedResult));

            log.warn("Transfer failed due to insufficient balance: transferId={}, walletId={}",
                    transfer.getId(), fromId);
            throw new InsufficientBalanceException(fromId, amount, sourceWallet.getBalance());
        }

        // 7. Debit source, credit destination
        sourceWallet.debit(amount);
        destWallet.credit(amount);
        walletRepository.save(sourceWallet);
        walletRepository.save(destWallet);

        // 8. Record ledger entries (double-entry)
        ledgerService.recordTransferEntries(fromId, toId, transfer.getId(), amount);

        // 9. Transition to PROCESSED
        transfer.transitionTo(TransferStatus.PROCESSED);
        transferRepository.save(transfer);

        // 10. Store idempotency record
        TransferResult result = toResult(transfer, false);
        idempotencyService.saveRecord(idempotencyKey, transfer.getId(),
                HttpStatus.CREATED.value(), serializeResult(result));

        log.info("Transfer completed: transferId={}, from={}, to={}, amount={}",
                transfer.getId(), fromId, toId, amount);
        return result;
    }

    private TransferResult toResult(Transfer transfer, boolean idempotentReplay) {
        return new TransferResult(
                transfer.getId(),
                transfer.getFromWalletId(),
                transfer.getToWalletId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                idempotentReplay
        );
    }

    private TransferResult deserializeResult(IdempotencyRecord record, boolean replay) {
        try {
            TransferResult original = objectMapper.readValue(
                    record.getResponseBody(), TransferResult.class);
            return new TransferResult(
                    original.transferId(),
                    original.fromWalletId(),
                    original.toWalletId(),
                    original.amount(),
                    original.status(),
                    original.createdAt(),
                    replay
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize idempotency response for key", e);
            throw new RuntimeException("Failed to deserialize cached transfer result", e);
        }
    }

    private String serializeResult(TransferResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transfer result", e);
            throw new RuntimeException("Failed to serialize transfer result", e);
        }
    }

    private UUID parseWalletId(String walletId) {
        try {
            return UUID.fromString(walletId);
        } catch (IllegalArgumentException e) {
            throw new InvalidTransferException("Invalid wallet ID format: " + walletId);
        }
    }
}
