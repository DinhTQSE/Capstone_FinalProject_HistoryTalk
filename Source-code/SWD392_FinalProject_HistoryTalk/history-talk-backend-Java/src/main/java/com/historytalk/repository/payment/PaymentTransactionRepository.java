package com.historytalk.repository.payment;

import com.historytalk.entity.payment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<com.historytalk.entity.payment.PaymentTransaction> findByReference(String reference);

    boolean existsByReference(String reference);
}