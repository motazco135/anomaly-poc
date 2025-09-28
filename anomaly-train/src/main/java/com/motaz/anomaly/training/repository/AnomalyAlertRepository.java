package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.AnomalyAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlertEntity, Long> {
}
