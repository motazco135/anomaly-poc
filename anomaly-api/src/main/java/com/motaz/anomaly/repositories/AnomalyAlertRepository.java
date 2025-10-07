package com.motaz.anomaly.repositories;


import com.motaz.anomaly.model.entities.AnomalyAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlertEntity, Long> {

    Stream<AnomalyAlertEntity> findAllByCustomerId(Long customerId);
}
