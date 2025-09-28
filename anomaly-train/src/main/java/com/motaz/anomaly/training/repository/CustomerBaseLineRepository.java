package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.CustomerBaselineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerBaseLineRepository extends JpaRepository<CustomerBaselineEntity, Long> {

}
