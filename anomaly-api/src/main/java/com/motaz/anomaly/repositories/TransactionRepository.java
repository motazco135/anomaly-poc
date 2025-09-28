package com.motaz.anomaly.repositories;


import com.motaz.anomaly.model.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findAllByCustomerIdOrderByTsUtcAsc(Long customerId);

    List<TransactionEntity> findAllByCustomerIdAndTsUtcAfterOrderByTsUtc(int customerId, Instant tsUtc);

}
