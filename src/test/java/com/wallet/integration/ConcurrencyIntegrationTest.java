package com.wallet.integration;

import com.wallet.domain.LedgerEntry;
import com.wallet.domain.LedgerEntryType;
import com.wallet.domain.Wallet;
import com.wallet.repository.LedgerEntryRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private static final String WALLET_A = "11111111-1111-1111-1111-111111111111";
    private static final String WALLET_B = "22222222-2222-2222-2222-222222222222";

    @Test
    void concurrentTransfers_noDoubleSpend() throws Exception {
        // Get initial balance
        Wallet source = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        long initialBalance = source.getBalance();

        int threadCount = 10;
        long transferAmount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Launch concurrent transfers — each with a unique idempotency key
        for (int i = 0; i < threadCount; i++) {
            String idempotencyKey = "concurrent-test-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                latch.await(); // Wait for all threads to be ready
                try {
                    var result = mockMvc.perform(post("/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "idempotencyKey": "%s",
                                                "fromWalletId": "%s",
                                                "toWalletId": "%s",
                                                "amount": %d
                                            }
                                            """.formatted(idempotencyKey, WALLET_A, WALLET_B, transferAmount)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                    return status;
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    return 500;
                }
            }));
        }

        // Release all threads at once
        latch.countDown();

        // Wait for all to complete
        for (Future<Integer> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Verify: source balance = initial - (successCount * amount)
        Wallet updatedSource = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        long expectedBalance = initialBalance - (successCount.get() * transferAmount);
        assertEquals(expectedBalance, updatedSource.getBalance(),
                "Source balance must reflect exactly the successful transfers");

        // Verify: balance never went negative
        assertTrue(updatedSource.getBalance() >= 0, "Balance must never be negative");

        // Verify: at least some succeeded and possibly some failed
        assertTrue(successCount.get() > 0, "At least one transfer should succeed");
        assertEquals(threadCount, successCount.get() + failCount.get(),
                "All threads should have completed");
    }

    @Test
    void concurrentTransfers_ledgerRemainsBalanced() throws Exception {
        int threadCount = 5;
        long transferAmount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String idempotencyKey = "ledger-concurrent-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    var result = mockMvc.perform(post("/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "idempotencyKey": "%s",
                                                "fromWalletId": "%s",
                                                "toWalletId": "%s",
                                                "amount": %d
                                            }
                                            """.formatted(idempotencyKey, WALLET_A, WALLET_B, transferAmount)))
                            .andReturn();
                    return result.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            }));
        }

        latch.countDown();
        for (Future<Integer> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Verify ledger invariant: sum of debits == sum of credits
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();

        long totalDebits = allEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        long totalCredits = allEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        assertEquals(totalDebits, totalCredits,
                "Ledger must remain balanced even under concurrent transfers");
    }

    @Test
    void duplicateIdempotencyKey_concurrentRequests_onlyOneSucceeds() throws Exception {
        String sharedKey = "duplicate-concurrent-" + UUID.randomUUID();
        int threadCount = 5;

        Wallet source = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        long balanceBefore = source.getBalance();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        String requestBody = """
                {
                    "idempotencyKey": "%s",
                    "fromWalletId": "%s",
                    "toWalletId": "%s",
                    "amount": 100
                }
                """.formatted(sharedKey, WALLET_A, WALLET_B);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    var result = mockMvc.perform(post("/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn();
                    return result.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            }));
        }

        latch.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        // Exactly one 201, rest should be 200 (idempotent replays) or handled gracefully
        long created = statuses.stream().filter(s -> s == 201).count();
        long ok = statuses.stream().filter(s -> s == 200).count();

        assertTrue(created <= 1, "At most one request should create the transfer");
        assertEquals(threadCount, created + ok + statuses.stream().filter(s -> s != 201 && s != 200).count(),
                "All requests should complete");

        // Balance should only be debited once
        Wallet updatedSource = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        assertEquals(balanceBefore - 100, updatedSource.getBalance(),
                "Balance should be debited exactly once despite concurrent duplicate requests");
    }
}
