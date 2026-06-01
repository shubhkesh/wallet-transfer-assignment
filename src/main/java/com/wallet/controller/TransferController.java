package com.wallet.controller;

import com.wallet.controller.dto.TransferRequest;
import com.wallet.controller.dto.TransferResponse;
import com.wallet.service.TransferResult;
import com.wallet.service.TransferService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody TransferRequest request) {

        log.debug("Received transfer request: idempotencyKey={}", request.idempotencyKey());

        TransferResult result = transferService.executeTransfer(
                request.idempotencyKey(),
                request.fromWalletId(),
                request.toWalletId(),
                request.amount()
        );

        TransferResponse response = TransferResponse.from(result);
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }
}
