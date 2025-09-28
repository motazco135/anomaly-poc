package com.motaz.anomaly.repositories;

import com.motaz.anomaly.model.documents.CustomerBaselineDocument;
import com.redis.om.spring.repository.RedisDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerBaselineDocumentRepository extends RedisDocumentRepository<CustomerBaselineDocument, String> {
    Optional<CustomerBaselineDocument> findByCustomerId(String customerId);
}
