package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.TransactionFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionFeatureRepository extends JpaRepository<TransactionFeatureEntity, Long> {

    List<TransactionFeatureEntity> findByCustomerIdAndIsTrainable(long customerId,boolean isTrainable);

    List<TransactionFeatureEntity> findByIsTrainable(boolean isTrainable);
}
