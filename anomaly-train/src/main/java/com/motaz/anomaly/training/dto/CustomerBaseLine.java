package com.motaz.anomaly.training.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerBaseLine {
    public Long customerId;
    public long priorCount = 0;
    public double priorMean = 0.0;
    public double priorM2 = 0.0;
    public double priorStd = 1.0;

    public final double[] segMean = new double[]{0, 0, 0, 0};
    public final int[] segCount = new int[]{0, 0, 0, 0};

    public final List<Double> allAmounts = new ArrayList<>();
    public final List<Double> nightAmounts = new ArrayList<>();
    public final List<Double> morningAmounts = new ArrayList<>();
    public final List<Double> afternoonAmounts = new ArrayList<>();
    public final List<Double> eveningAmounts = new ArrayList<>();
}
