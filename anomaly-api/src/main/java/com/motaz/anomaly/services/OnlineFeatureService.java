package com.motaz.anomaly.services;

import com.motaz.anomaly.dto.OnlineFeaturesDto;
import com.motaz.anomaly.repositories.CustomerBaselineDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineFeatureService {

    private final CustomerBaselineDocumentRepository customerBaselineDocumentRepository;

    public OnlineFeaturesDto compute(long customerId, double amount, Instant tsUtc) {
        log.info("Computing features for customerId:{} ", customerId);

        var customerBaselineDocument = customerBaselineDocumentRepository.findByCustomerId(String.valueOf(customerId))
                .orElseThrow(() -> new IllegalStateException("No baseline in Redis for customer " + customerId));
        log.info("Customer baseline document found: {}", customerBaselineDocument);

        double mean = 0, std = 0, median = 0, segNight = 0, segMorning = 0, segAfternoon = 0, segEvening = 0;
        mean = customerBaselineDocument.getMeanAmount();
        std = customerBaselineDocument.getStdAmount();
        median = customerBaselineDocument.getMedianAmount();
        segNight = customerBaselineDocument.getSegMeanNight();
        segMorning = customerBaselineDocument.getSegMeanMorning();
        segAfternoon = customerBaselineDocument.getSegMeanAfternoon();
        segEvening = customerBaselineDocument.getSegMeanEvening();

        int hour = ZonedDateTime.ofInstant(tsUtc, ZoneOffset.UTC).getHour();
        int seg = segmentOfHour(hour);
        double segMean = switch (seg) {
            case 0 -> segNight;
            case 1 -> segMorning;
            case 2 -> segAfternoon;
            default -> segEvening;
        };

        double safeStd  = Math.max(std, 1.0);
        double safeMean = Math.max(mean, 1.0);
        double safeSeg  = Math.max(segMean, 1.0);
        double safeMed  = Math.max(median, 1.0);

        double amountZScore     = (amount - mean) / safeStd;
        double timeSegmentRatio = amount / safeSeg;
        double velocityRatio    = amount / safeMean;
        double medianDeviation  = amount / safeMed;
        double[] realTimeFeatureSet = { amountZScore, timeSegmentRatio, velocityRatio, medianDeviation };

        OnlineFeaturesDto onlineFeaturesDto = OnlineFeaturesDto.builder()
                .customerId(customerId)
                .baseLineMean(mean)
                .baseLineMedian(median)
                .baseLineStdDeviation(std)
                .baseLineSegNight(segNight)
                .baseLineSegMorning(segMorning)
                .baseLineSegAfternoon(segAfternoon)
                .baseLineSegEvening(segEvening)
                .baseLineSegOfHour(seg)
                .baseLineSegMean(segMean)

                .amountZScore(amountZScore)
                .timeSegmentRatio(timeSegmentRatio)
                .velocityRatio(velocityRatio)
                .medianDeviation(medianDeviation).build();

        log.info("Customer Online features : {}", onlineFeaturesDto);
        log.info("OnlineFeature compute Completed ...");
        return onlineFeaturesDto;
    }


    /** Simple hour→segment mapping (0 Night, 1 Morning, 2 Afternoon, 3 Evening). */
    private static int segmentOfHour(int h){
        if (h<=5) return 0;
        if (h<=11) return 1;
        if (h<=17) return 2;
        return 3;
    }
}
