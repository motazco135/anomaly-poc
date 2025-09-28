package com.motaz.anomaly.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class FundTransferRequestDto {
    private Long customerId;
    private double amount;
    private String channel;
    private Instant tsUtc = Instant.now();
}
