package com.wallet.service;

import com.wallet.domain.IdempotencyRecord;
import com.wallet.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        return idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public void saveRecord(String idempotencyKey, UUID transferId,
                            int responseCode, String responseBody) {
        IdempotencyRecord record = IdempotencyRecord.create(
                idempotencyKey, transferId, responseCode, responseBody);
        idempotencyRecordRepository.save(record);
    }
}
