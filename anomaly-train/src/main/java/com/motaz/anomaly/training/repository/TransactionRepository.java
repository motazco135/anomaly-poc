package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findAllByCustomerIdOrderByTsUtcAsc(Long customerId);

    List<TransactionEntity> findAllByCustomerIdAndTsUtcAfterOrderByTsUtc(int customerId, Instant tsUtc);

}
