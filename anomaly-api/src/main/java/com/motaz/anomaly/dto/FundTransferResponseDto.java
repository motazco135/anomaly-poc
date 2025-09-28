package com.motaz.anomaly.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FundTransferResponseDto {

    private Decision decision;
    private double score;
    private double threshold;
    private long modelId;
    private OnlineFeaturesDto  onlineFeatures;
}
