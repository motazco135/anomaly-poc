package com.motaz.anomaly.services;

import com.motaz.anomaly.dto.Decision;
import com.motaz.anomaly.dto.FundTransferRequestDto;
import com.motaz.anomaly.dto.FundTransferResponseDto;
import com.motaz.anomaly.dto.OnlineFeaturesDto;
import com.motaz.anomaly.model.entities.AnomalyAlertEntity;
import com.motaz.anomaly.model.entities.TransactionEntity;
import com.motaz.anomaly.repositories.AnomalyAlertRepository;
import com.motaz.anomaly.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smile.anomaly.IsolationForest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyScoringService {

    private final OnlineFeatureService featureService;
    private final ModelRegistryService modelRegistryService;
    private final AnomalyAlertRepository anomalyAlertRepository;
    private final ThresholdService thresholdService;
    private final TransactionRepository transactionRepository;

    @Transactional
    public FundTransferResponseDto score(FundTransferRequestDto fundTransferRequestDto) throws IOException, ClassNotFoundException {
        log.info("---Start Score Fund Transfer Request : {}", fundTransferRequestDto);
        OnlineFeaturesDto realTimeFeatureSet = featureService.compute(fundTransferRequestDto.getCustomerId(), fundTransferRequestDto.getAmount(),
                fundTransferRequestDto.getTsUtc());
        //scoring
        IsolationForest iForest = modelRegistryService.loadLatestModel();
        double score = iForest.score(new double[]{ 
                  realTimeFeatureSet.getAmountZScore()
                , realTimeFeatureSet.getTimeSegmentRatio()
                , realTimeFeatureSet.getVelocityRatio()
                , realTimeFeatureSet.getMedianDeviation()
        });
        
        Decision decision = Decision.ALLOW;
        if (thresholdService.block() != null && score >= thresholdService.block()) {
            decision = Decision.BLOCK;
        } else if (score >= thresholdService.review()) {
            decision = Decision.UNDER_REVIEW;
        }
        log.info("Score Fund Transfer Request decision : {} ,iForest score: {}", decision,score);

        //Save Transaction to T_transaction table
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setCustomerId(fundTransferRequestDto.getCustomerId());
        transactionEntity.setCurrencyCode("SAR");
        transactionEntity.setAmount(new BigDecimal(fundTransferRequestDto.getAmount()));
        transactionEntity.setChannel(fundTransferRequestDto.getChannel());
        transactionEntity.setTsUtc(fundTransferRequestDto.getTsUtc());
        final TransactionEntity savedTransaction = transactionRepository.save(transactionEntity);


        // Log only anomalous decisions (UNDER_REVIEW/BLOCK)
        if (decision != Decision.ALLOW) {
            // Log to Transaction Alert Table
            AnomalyAlertEntity anomalyAlertEntity = new AnomalyAlertEntity();
            anomalyAlertEntity.setCustomerId(fundTransferRequestDto.getCustomerId());
            anomalyAlertEntity.setTxnId(savedTransaction.getId().toString());
            anomalyAlertEntity.setAmount(new BigDecimal(fundTransferRequestDto.getAmount()));
            anomalyAlertEntity.setCurrencyCode("SAR");
            anomalyAlertEntity.setChannel(fundTransferRequestDto.getChannel());
            anomalyAlertEntity.setTsUtc(fundTransferRequestDto.getTsUtc());
            anomalyAlertEntity.setScore(new BigDecimal(score));
            anomalyAlertEntity.setValidationDecision(decision.name());


            String featuresJson = String.format(
                    "{\"amountZScore\": %.6f, \"timeSegmentRatio\": %.6f, \"velocityRatio\": %.6f, \"medianDeviation\": %.6f}",
                    realTimeFeatureSet.getAmountZScore()
                    , realTimeFeatureSet.getTimeSegmentRatio()
                    , realTimeFeatureSet.getVelocityRatio()
                    , realTimeFeatureSet.getMedianDeviation()
            );

            String baselineJson = String.format(
                    "{\"mean\": %.6f, \"std\": %.6f, \"median\": %.6f, \"segmentIndex\":  %.6f, \"segmentMean\": %.6f}",
                    realTimeFeatureSet.getBaseLineMean()
                    , realTimeFeatureSet.getBaseLineStdDeviation()
                    , realTimeFeatureSet.getBaseLineMedian()
                    , realTimeFeatureSet.getBaseLineSegOfHour()
                    , realTimeFeatureSet.getBaseLineSegMean()
            );

            Map<String, Object> factsJson = new HashMap<>();
            factsJson.put("features", featuresJson);
            factsJson.put("baseline", baselineJson);
            anomalyAlertEntity.setFactsJson(factsJson);

            anomalyAlertRepository.save(anomalyAlertEntity);
        }

        log.info("--- Score Fund Transfer Request Completed ....");

        return FundTransferResponseDto.builder()
                .decision(decision)
                .score(score)
                .threshold(thresholdService.review())
                .onlineFeatures(realTimeFeatureSet)
                .build();
    }

}
