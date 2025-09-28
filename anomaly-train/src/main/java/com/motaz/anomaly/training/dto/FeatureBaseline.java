package com.motaz.anomaly.training.dto;


import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FeatureBaseline {

    private static final int TIME_SEGMENTS = 4; // 0 Night 00–05, 1 Morning 06–11, 2 Afternoons 12–17, 3 Evenings 18–23
    public long n = 0;
    public double mean = 0, m2 = 0, std = 1;
    public final double[] segMean = new double[TIME_SEGMENTS];
    public final int[] segCount = new int[TIME_SEGMENTS];
    public final List<Double> allAmounts = new ArrayList<>();

    public void print(){
        log.info("FeatureBaseline n:{}, mean:{}, m2:{}, std:{}, segMean:{}, segCount:{},allAmounts:{}",
                n, mean, m2, std, segMean, segCount, allAmounts);
    }
}
