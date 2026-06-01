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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private IdempotencyService idempotencyService;

    private TransferServiceImpl transferService;

    private static final UUID WALLET_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WALLET_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String IDEMPOTENCY_KEY = "test-key-123";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        transferService = new TransferServiceImpl(
                walletRepository, transferRepository,
                ledgerService, idempotencyService, objectMapper);
    }

    @Test
    void executeTransfer_happyPath() {
        Wallet sourceWallet = Wallet.create(WALLET_A_ID, 1000L);
        Wallet destWallet = Wallet.create(WALLET_B_ID, 500L);

        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_A_ID)).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdForUpdate(WALLET_B_ID)).thenReturn(Optional.of(destWallet));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = transferService.executeTransfer(
                IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_B_ID.toString(), 300L);

        assertNotNull(result.transferId());
        assertEquals(WALLET_A_ID, result.fromWalletId());
        assertEquals(WALLET_B_ID, result.toWalletId());
        assertEquals(300L, result.amount());
        assertEquals(TransferStatus.PROCESSED, result.status());
        assertFalse(result.idempotentReplay());

        assertEquals(700L, sourceWallet.getBalance());
        assertEquals(800L, destWallet.getBalance());

        verify(ledgerService).recordTransferEntries(
                eq(WALLET_A_ID), eq(WALLET_B_ID), any(UUID.class), eq(300L));
        verify(idempotencyService).saveRecord(
                eq(IDEMPOTENCY_KEY), any(UUID.class), eq(201), anyString());
        verify(transferRepository, times(2)).save(any(Transfer.class));
    }

    @Test
    void executeTransfer_idempotentReplay() {
        String responseBody = """
                {"transferId":"%s","fromWalletId":"%s","toWalletId":"%s","amount":300,"status":"PROCESSED","createdAt":"2026-01-01T00:00:00Z","idempotentReplay":false}
                """.formatted(UUID.randomUUID(), WALLET_A_ID, WALLET_B_ID).trim();

        IdempotencyRecord record = IdempotencyRecord.create(
                IDEMPOTENCY_KEY, UUID.randomUUID(), 201, responseBody);

        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(record));

        TransferResult result = transferService.executeTransfer(
                IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_B_ID.toString(), 300L);

        assertTrue(result.idempotentReplay());
        assertEquals(300L, result.amount());
        assertEquals(TransferStatus.PROCESSED, result.status());

        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(transferRepository, never()).save(any());
        verify(ledgerService, never()).recordTransferEntries(any(), any(), any(), any(long.class));
    }

    @Test
    void executeTransfer_insufficientBalance() {
        Wallet sourceWallet = Wallet.create(WALLET_A_ID, 50L);
        Wallet destWallet = Wallet.create(WALLET_B_ID, 500L);

        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_A_ID)).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdForUpdate(WALLET_B_ID)).thenReturn(Optional.of(destWallet));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> transferService.executeTransfer(
                        IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_B_ID.toString(), 100L));

        assertEquals(WALLET_A_ID, ex.getWalletId());
        assertEquals(100L, ex.getRequested());
        assertEquals(50L, ex.getAvailable());

        verify(ledgerService, never()).recordTransferEntries(any(), any(), any(), any(long.class));

        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, times(2)).save(captor.capture());
        Transfer savedTransfer = captor.getAllValues().get(1);
        assertEquals(TransferStatus.FAILED, savedTransfer.getStatus());
    }

    @Test
    void executeTransfer_sourceWalletNotFound() {
        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_A_ID)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class,
                () -> transferService.executeTransfer(
                        IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_B_ID.toString(), 100L));

        verify(transferRepository, never()).save(any());
        verify(ledgerService, never()).recordTransferEntries(any(), any(), any(), any(long.class));
    }

    @Test
    void executeTransfer_destWalletNotFound() {
        Wallet sourceWallet = Wallet.create(WALLET_A_ID, 1000L);

        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_A_ID)).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdForUpdate(WALLET_B_ID)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class,
                () -> transferService.executeTransfer(
                        IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_B_ID.toString(), 100L));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void executeTransfer_sameWalletRejected() {
        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThrows(InvalidTransferException.class,
                () -> transferService.executeTransfer(
                        IDEMPOTENCY_KEY, WALLET_A_ID.toString(), WALLET_A_ID.toString(), 100L));

        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void executeTransfer_invalidWalletIdFormat() {
        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThrows(InvalidTransferException.class,
                () -> transferService.executeTransfer(
                        IDEMPOTENCY_KEY, "not-a-uuid", WALLET_B_ID.toString(), 100L));
    }

    @Test
    void executeTransfer_locksWalletsInDeterministicOrder() {
        Wallet sourceWallet = Wallet.create(WALLET_B_ID, 1000L);
        Wallet destWallet = Wallet.create(WALLET_A_ID, 500L);

        when(idempotencyService.findByKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_A_ID)).thenReturn(Optional.of(destWallet));
        when(walletRepository.findByIdForUpdate(WALLET_B_ID)).thenReturn(Optional.of(sourceWallet));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = transferService.executeTransfer(
                IDEMPOTENCY_KEY, WALLET_B_ID.toString(), WALLET_A_ID.toString(), 200L);

        assertEquals(TransferStatus.PROCESSED, result.status());
        assertEquals(800L, sourceWallet.getBalance());
        assertEquals(700L, destWallet.getBalance());
    }
}
