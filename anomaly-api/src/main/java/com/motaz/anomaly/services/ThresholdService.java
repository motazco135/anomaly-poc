package com.motaz.anomaly.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThresholdService {

    @Value("${anomaly.threshold.review}")
    private double reviewThreshold;

    @Value("${anomaly.threshold.block:0}")
    private double blockThreshold;

    public double review() {
        return reviewThreshold;
    }
    public Double block()  {
        return blockThreshold > 0 ? blockThreshold : null;
    }
}
