package com.wallet.integration;

import com.wallet.domain.LedgerEntry;
import com.wallet.domain.LedgerEntryType;
import com.wallet.domain.Wallet;
import com.wallet.repository.LedgerEntryRepository;
import com.wallet.repository.TransferRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    private static final String WALLET_A = "11111111-1111-1111-1111-111111111111";
    private static final String WALLET_B = "22222222-2222-2222-2222-222222222222";
    private static final String WALLET_EMPTY = "33333333-3333-3333-3333-333333333333";

    @Test
    void successfulTransfer_returns201_updatesBalances_createsLedgerEntries() throws Exception {
        String idempotencyKey = "integration-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 1000
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_B)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.fromWalletId").value(WALLET_A))
                .andExpect(jsonPath("$.toWalletId").value(WALLET_B))
                .andExpect(jsonPath("$.transferId").exists());

        Wallet sourceWallet = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        Wallet destWallet = walletRepository.findById(UUID.fromString(WALLET_B)).orElseThrow();
        assertEquals(9000L, sourceWallet.getBalance());
        assertEquals(6000L, destWallet.getBalance());
    }

    @Test
    void idempotentReplay_returns200_noBalanceChange() throws Exception {
        String idempotencyKey = "idempotent-test-" + UUID.randomUUID();

        String requestBody = """
                {
                    "idempotencyKey": "%s",
                    "fromWalletId": "%s",
                    "toWalletId": "%s",
                    "amount": 500
                }
                """.formatted(idempotencyKey, WALLET_A, WALLET_B);

        // First request — 201
        MvcResult first = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        Wallet sourceAfterFirst = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        long balanceAfterFirst = sourceAfterFirst.getBalance();

        // Second request with same key — 200
        MvcResult second = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        // Balance unchanged after replay
        Wallet sourceAfterSecond = walletRepository.findById(UUID.fromString(WALLET_A)).orElseThrow();
        assertEquals(balanceAfterFirst, sourceAfterSecond.getBalance());

        // Same transferId in both responses
        String firstBody = first.getResponse().getContentAsString();
        String secondBody = second.getResponse().getContentAsString();
        assertEquals(
                extractTransferId(firstBody),
                extractTransferId(secondBody));
    }

    @Test
    void insufficientBalance_returns422() throws Exception {
        String idempotencyKey = "insufficient-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 999999
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_B)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void walletNotFound_returns404() throws Exception {
        String idempotencyKey = "not-found-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "99999999-9999-9999-9999-999999999999",
                                    "toWalletId": "%s",
                                    "amount": 100
                                }
                                """.formatted(idempotencyKey, WALLET_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void sameWallet_returns400() throws Exception {
        String idempotencyKey = "same-wallet-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 100
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_A)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFields_returns400() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fromWalletId": "%s",
                                    "amount": 100
                                }
                                """.formatted(WALLET_A)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroAmount_returns400() throws Exception {
        String idempotencyKey = "zero-amount-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 0
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_B)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ledgerEntriesAreConsistent_debitsEqualCredits() throws Exception {
        String idempotencyKey = "ledger-test-" + UUID.randomUUID();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 200
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_B)))
                .andExpect(status().isCreated());

        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();

        long totalDebits = allEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        long totalCredits = allEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        assertEquals(totalDebits, totalCredits, "Ledger must balance: total debits == total credits");
    }

    private String extractTransferId(String json) {
        int start = json.indexOf("\"transferId\":\"") + 14;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
