package com.codegym.repository;

import com.codegym.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTxnRef(String txnRef);
    List<PaymentTransaction> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}
