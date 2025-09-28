package com.motaz.anomaly.repositories;


import com.motaz.anomaly.model.entities.AnomalyAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlertEntity, Long> {
}
