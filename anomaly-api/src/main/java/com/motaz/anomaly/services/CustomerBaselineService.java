package com.motaz.anomaly.services;

import com.motaz.anomaly.model.documents.CustomerBaselineDocument;
import com.motaz.anomaly.model.entities.CustomerBaselineEntity;
import com.motaz.anomaly.repositories.CustomerBaseLineRepository;
import com.motaz.anomaly.repositories.CustomerBaselineDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBaselineService {

    private final CustomerBaselineDocumentRepository customerBaselineDocumentRepository;
    private final CustomerBaseLineRepository customerBaseLineRepository;

    @Transactional(readOnly = true)
    public void cashCustomerBaseLine(){
        try (Stream<CustomerBaselineEntity> customerStream = customerBaseLineRepository.findAllBy()) {
            customerStream.forEach(customerBaseLine -> {
                var customerBaselineDocument = CustomerBaselineDocument.builder()
                        .id("cust:" + customerBaseLine.getId())
                        .customerId(customerBaseLine.getId())
                        .meanAmount(customerBaseLine.getMeanAmount())
                        .stdAmount(customerBaseLine.getStdAmount())
                        .medianAmount(customerBaseLine.getMedianAmount())
                        .segMeanNight(customerBaseLine.getSegMeanNight())
                        .segMeanMorning(customerBaseLine.getSegMeanMorning())
                        .segMeanAfternoon(customerBaseLine.getSegMeanAfternoon())
                        .segMeanEvening(customerBaseLine.getSegMeanEvening()).build();
                customerBaselineDocumentRepository.save(customerBaselineDocument);
            });
        }
        log.info("customers cached in Redis..");
    }
}
