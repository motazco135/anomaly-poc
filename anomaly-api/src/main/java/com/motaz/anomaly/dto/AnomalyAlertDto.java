package com.motaz.anomaly.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AnomalyAlertDto {
    private String txnId;
    private BigDecimal amount;
    private String currencyCode;
    private String channel;
    private Instant tsUtc;
    private BigDecimal score;
    private String severity;
    private Map<String, Object> factsJson;
    private String agentStatus;
    private String validationDecision ;
    private String explanation;
    private String summary;
    private String mainFactor;
}
