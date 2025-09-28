package com.motaz.anomaly.repositories;


import com.motaz.anomaly.model.entities.CustomerBaselineEntity;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface CustomerBaseLineRepository extends JpaRepository<CustomerBaselineEntity, Long> {

    @QueryHints(value = @QueryHint(name = org.hibernate.jpa.AvailableHints.HINT_FETCH_SIZE, value = "1000"))
    Stream<CustomerBaselineEntity> findAllBy();

}
