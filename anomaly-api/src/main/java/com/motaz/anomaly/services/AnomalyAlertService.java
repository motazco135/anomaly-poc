package com.motaz.anomaly.services;

import com.motaz.anomaly.dto.AnomalyAlertDto;
import com.motaz.anomaly.model.entities.AnomalyAlertEntity;
import com.motaz.anomaly.model.entities.CustomerBaselineEntity;
import com.motaz.anomaly.repositories.AnomalyAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AnomalyAlertService {

    private final AnomalyAlertRepository anomalyAlertRepository;

    @Transactional(readOnly = true)
    public List<AnomalyAlertDto> findAllByCustomerId(Long customerId){
        List<AnomalyAlertDto> anomalyAlertDtos = new ArrayList<>();
        try (Stream<AnomalyAlertEntity> customerStream = anomalyAlertRepository.findAllByCustomerId(customerId)) {
            customerStream.forEach(customerAlert -> {
                anomalyAlertDtos.add(AnomalyAlertDto.builder()
                                .txnId(customerAlert.getTxnId())
                                .amount(customerAlert.getAmount())
                                .currencyCode(customerAlert.getCurrencyCode())
                                .channel(customerAlert.getChannel())
                                .tsUtc(customerAlert.getTsUtc())
                                .score(customerAlert.getScore())
                                .severity(customerAlert.getSeverity())
                                .factsJson(customerAlert.getFactsJson())
                                .agentStatus(customerAlert.getAgentStatus())
                                .validationDecision(customerAlert.getValidationDecision())
                                .explanation(customerAlert.getLlmExplanationJson())
                                .summary(customerAlert.getLlmSummary())
                                .mainFactor(customerAlert.getLlmMainFactor())
                        .build());
            });
        }
        return anomalyAlertDtos;
    }
}
