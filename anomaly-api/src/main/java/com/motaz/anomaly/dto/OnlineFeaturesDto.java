package com.motaz.anomaly.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlineFeaturesDto {

    private Long customerId;
    private double amountZScore;
    private double timeSegmentRatio;
    private double velocityRatio;
    private double medianDeviation;

    private double baseLineMean;
    private double baseLineStdDeviation;
    private double baseLineMedian;
    private double baseLineSegNight;
    private double baseLineSegMorning;
    private double baseLineSegAfternoon;
    private double baseLineSegEvening;
    private double baseLineSegOfHour;
    private double baseLineSegMean;

}
