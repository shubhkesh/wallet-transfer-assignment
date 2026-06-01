package com.wallet.repository;

import com.wallet.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    List<Transfer> findByFromWalletIdOrToWalletId(UUID fromWalletId, UUID toWalletId);
}
