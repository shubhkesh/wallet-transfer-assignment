package com.wallet.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String WALLET_A = "11111111-1111-1111-1111-111111111111";
    private static final String WALLET_B = "22222222-2222-2222-2222-222222222222";

    @Test
    void getWallet_returnsBalance() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}", WALLET_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WALLET_A))
                .andExpect(jsonPath("$.balance").value(10000))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void getWallet_notFound() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransferHistory_returnsTransfers() throws Exception {
        String idempotencyKey = "history-test-" + UUID.randomUUID();

        // Create a transfer first
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "idempotencyKey": "%s",
                                    "fromWalletId": "%s",
                                    "toWalletId": "%s",
                                    "amount": 500
                                }
                                """.formatted(idempotencyKey, WALLET_A, WALLET_B)))
                .andExpect(status().isCreated());

        // Check history for source wallet
        mockMvc.perform(get("/wallets/{walletId}/transfers", WALLET_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromWalletId").value(WALLET_A))
                .andExpect(jsonPath("$[0].toWalletId").value(WALLET_B))
                .andExpect(jsonPath("$[0].amount").value(500))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"));

        // Check history for destination wallet
        mockMvc.perform(get("/wallets/{walletId}/transfers", WALLET_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(500));
    }

    @Test
    void getTransferHistory_walletNotFound() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}/transfers", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransferHistory_emptyForNewWallet() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}/transfers", "33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
