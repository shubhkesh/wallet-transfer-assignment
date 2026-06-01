package com.wallet.controller;

import com.wallet.controller.dto.TransferHistoryResponse;
import com.wallet.controller.dto.WalletResponse;
import com.wallet.domain.Transfer;
import com.wallet.domain.Wallet;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransferRepository;
import com.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class WalletController {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;

    public WalletController(WalletRepository walletRepository,
                            TransferRepository transferRepository) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID walletId) {
        log.debug("Fetching wallet: {}", walletId);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        return ResponseEntity.ok(WalletResponse.from(wallet));
    }

    @GetMapping("/wallets/{walletId}/transfers")
    public ResponseEntity<List<TransferHistoryResponse>> getTransferHistory(
            @PathVariable UUID walletId) {
        log.debug("Fetching transfer history for wallet: {}", walletId);

        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }

        List<Transfer> transfers = transferRepository
                .findByFromWalletIdOrToWalletId(walletId, walletId);

        List<TransferHistoryResponse> history = transfers.stream()
                .map(TransferHistoryResponse::from)
                .toList();

        return ResponseEntity.ok(history);
    }
}
