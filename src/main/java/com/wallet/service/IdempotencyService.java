package com.wallet.service;

import com.wallet.domain.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyService {

    Optional<IdempotencyRecord> findByKey(String idempotencyKey);

    void saveRecord(String idempotencyKey, UUID transferId,
                    int responseCode, String responseBody);
}
