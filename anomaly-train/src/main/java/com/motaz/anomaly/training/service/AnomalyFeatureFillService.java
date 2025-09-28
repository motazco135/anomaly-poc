package com.motaz.anomaly.training.service;

import com.motaz.anomaly.training.dto.CustomerBaseLine;
import com.motaz.anomaly.training.dto.FeatureBaseline;
import com.motaz.anomaly.training.model.CustomerBaselineEntity;
import com.motaz.anomaly.training.model.TransactionEntity;
import com.motaz.anomaly.training.model.TransactionFeatureEntity;
import com.motaz.anomaly.training.repository.CustomerBaseLineRepository;
import com.motaz.anomaly.training.repository.TransactionFeatureRepository;
import com.motaz.anomaly.training.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

/**
 * For each customer get the transaction list for each transaction compute
 * features and save it in the t_transaction_feature table
* */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyFeatureFillService {

    private final TransactionRepository transactionRepository;
    private final TransactionFeatureRepository transactionFeatureRepository;
    private final CustomerBaseLineRepository customerBaseLineRepository;

    private static final int TIME_SEGMENTS = 4; // 0 Night 00–05, 1 Morning 06–11, 2 Afternoons 12–17, 3 Evenings 18–23
    private static final double EPS = 1.0;

    public List<Long> getCustomerIds(){
        List<Long> customerIds = new ArrayList<>();
        for (int i = 1; i <=500 ; i++) {
            customerIds.add(Long.valueOf(i));
        }
        return customerIds;
    }

    public List<TransactionEntity> getTransactionsByCustomerId(Long customerId){
        List<TransactionEntity> transactions = transactionRepository.findAllByCustomerIdOrderByTsUtcAsc(customerId);
        log.info("Found {} transactions , for customerId: {} ", transactions.size(),customerId);
        return transactions;
    }

    public void doFeatureFill(){
        Map<Long, FeatureBaseline> state = new HashMap<>();
        List<Long> customerIds = getCustomerIds();
        for (int i = 0; i < customerIds.size(); i++) {
            Long customerId = customerIds.get(i);
            CustomerBaseLine customerBaseLine = new CustomerBaseLine();
            customerBaseLine.customerId = customerId;
            //TODO: Pagination is missing
            //get Customer transactions
            List<TransactionEntity> transactions = getTransactionsByCustomerId(customerId);
            List<TransactionFeatureEntity> transactionFeatureEntityList = new ArrayList<>();
            for (int j = 0; j < transactions.size(); j++) {
                TransactionEntity transaction = transactions.get(j);

                // Convert the Instant to LocalDateTime
                LocalDateTime localDateTime = LocalDateTime.ofInstant(transaction.getTsUtc(), ZoneOffset.UTC);
                int hour        = localDateTime.atZone(ZoneOffset.UTC).getHour();
                int seg         = segmentOfHour(hour);
                log.info("Transaction id :{},CustomerID:{} , Segment {}", transaction.getId(), customerId, seg);

                FeatureBaseline  featureBaseline = state.computeIfAbsent(transaction.getCustomerId(),k->new FeatureBaseline());
                // ----- PRIOR baseline values (before including current txn) -----
                double safeMean = max(featureBaseline.mean, EPS);
                double safeStd  = max(featureBaseline.std, EPS);
                double segMean  = featureBaseline.segCount[seg] > 0 ? featureBaseline.segMean[seg] : safeMean;
                double safeSegMean = max(segMean, EPS);
                double priorMedian = max(computeMedian(featureBaseline.allAmounts), EPS);

                // 4 features
                double amountZScore     = (transaction.getAmount().doubleValue() - featureBaseline.mean) / safeStd;
                double timeSegmentRatio = transaction.getAmount().doubleValue()  / safeSegMean;
                double velocityRatio    = transaction.getAmount().doubleValue()  / safeMean;     // your definition
                double medianDeviation  = transaction.getAmount().doubleValue()  / priorMedian;
                log.info("Computed Features: amountZScore:{}, timeSegmentRatio:{}, velocityRatio:{}, medianDeviation:{}",
                        amountZScore, timeSegmentRatio, velocityRatio, medianDeviation);

                // insert t_transaction_feature
                TransactionFeatureEntity transactionFeatureEntity = new TransactionFeatureEntity();
                transactionFeatureEntity.setTxn(transaction);
                transactionFeatureEntity.setCustomerId(customerId);
                transactionFeatureEntity.setTsUtc(transaction.getTsUtc());
                transactionFeatureEntity.setAmountZScore(amountZScore);
                transactionFeatureEntity.setAmount(transaction.getAmount().doubleValue());
                transactionFeatureEntity.setCurrencyCode(transaction.getCurrencyCode());
                transactionFeatureEntity.setTimeSegmentRatio(timeSegmentRatio);
                transactionFeatureEntity.setVelocityRatio(velocityRatio);
                transactionFeatureEntity.setMedianDeviation(medianDeviation);

                transactionFeatureEntity.setBaselineN(featureBaseline.n);
                transactionFeatureEntity.setBaselineMeanAmount(featureBaseline.mean);
                transactionFeatureEntity.setBaselineStdAmount(featureBaseline.std);
                transactionFeatureEntity.setBaselineMedianAmount(priorMedian);
                transactionFeatureEntity.setBaselineSegIndex(seg);
                transactionFeatureEntity.setBaselineSegMean(segMean);
                transactionFeatureEntity.setIsTrainable((featureBaseline.n >= 10) && (featureBaseline.std >= 1.0));
                transactionFeatureEntityList.add(transactionFeatureEntity);

                //------ UPDATE Customer BaseLine
                customerBaseLine.allAmounts.add(transaction.getAmount().doubleValue());
                switch (seg) {
                    case 0 -> customerBaseLine.nightAmounts.add(transaction.getAmount().doubleValue());
                    case 1 -> customerBaseLine.morningAmounts.add(transaction.getAmount().doubleValue());
                    case 2 -> customerBaseLine.afternoonAmounts.add(transaction.getAmount().doubleValue());
                    default -> customerBaseLine.eveningAmounts.add(transaction.getAmount().doubleValue());
                }

                // Welford for mean/std
                customerBaseLine.priorCount += 1;
                double customerDelta = transaction.getAmount().doubleValue() - customerBaseLine.priorMean;
                customerBaseLine.priorMean += customerDelta / customerBaseLine.priorCount;
                double customerDelta2 = transaction.getAmount().doubleValue() - customerBaseLine.priorMean;
                customerBaseLine.priorM2 += customerDelta * customerDelta2;
                customerBaseLine.priorStd = (customerBaseLine.priorCount > 1) ? max(Math.sqrt(customerBaseLine.priorM2 / (customerBaseLine.priorCount - 1)), 1.0) : customerBaseLine.priorStd;
                // online segment mean
                customerBaseLine.segCount[seg] += 1;
                double customerPrev = customerBaseLine.segMean[seg];
                customerBaseLine.segMean[seg] = customerPrev + (transaction.getAmount().doubleValue() - customerPrev) / customerBaseLine.segCount[seg];

                // ----- UPDATE Feature baseline with current txn (Welford + seg means + amounts list) -----
                featureBaseline.allAmounts.add(transaction.getAmount().doubleValue());
                featureBaseline.n += 1;
                double delta  = transaction.getAmount().doubleValue() - featureBaseline.mean;
                featureBaseline.mean += delta / featureBaseline.n;
                double delta2 = transaction.getAmount().doubleValue() - featureBaseline.mean;
                featureBaseline.m2 += delta * delta2;
                featureBaseline.std = (featureBaseline.n > 1) ? sqrt(featureBaseline.m2 / (featureBaseline.n - 1)) : max(featureBaseline.std, EPS);
                featureBaseline.segCount[seg] += 1;
                double prev = featureBaseline.segMean[seg];
                featureBaseline.segMean[seg] = prev + (transaction.getAmount().doubleValue() - prev) / featureBaseline.segCount[seg];
                featureBaseline.print();
            }
            saveCustomerBaseline(customerBaseLine);
            transactionFeatureRepository.saveAll(transactionFeatureEntityList);
        }
    }

    private void saveCustomerBaseline(CustomerBaseLine customerBaseLine){
        if (!customerBaseLine.allAmounts.isEmpty()) {
            CustomerBaselineEntity customerBaselineEntity = new CustomerBaselineEntity();
            double meanAll   = mean(customerBaseLine.allAmounts);
            double stdAll    = stdAround(meanAll, customerBaseLine.allAmounts);
            double medianAll = median(customerBaseLine.allAmounts);

            double segNightMean     = customerBaseLine.nightAmounts.isEmpty()     ? meanAll : mean(customerBaseLine.nightAmounts);
            double segMorningMean   = customerBaseLine.morningAmounts.isEmpty()   ? meanAll : mean(customerBaseLine.morningAmounts);
            double segAfternoonMean = customerBaseLine.afternoonAmounts.isEmpty() ? meanAll : mean(customerBaseLine.afternoonAmounts);
            double segEveningMean   = customerBaseLine.eveningAmounts.isEmpty()   ? meanAll : mean(customerBaseLine.eveningAmounts);

            customerBaselineEntity.setId(customerBaseLine.customerId);
            customerBaselineEntity.setNTx(Long.valueOf(customerBaseLine.allAmounts.size()));
            customerBaselineEntity.setMeanAmount(meanAll);
            customerBaselineEntity.setMedianAmount(medianAll);
            customerBaselineEntity.setStdAmount(stdAll);
            customerBaselineEntity.setSegMeanNight(segNightMean);
            customerBaselineEntity.setSegMeanMorning(segMorningMean);
            customerBaselineEntity.setSegMeanAfternoon(segAfternoonMean);
            customerBaselineEntity.setSegMeanEvening(segEveningMean);
            customerBaseLineRepository.save(customerBaselineEntity);
        }
    }

    private static int segmentOfHour(int hour) {
        if (hour <= 5)  return 0; // Night
        if (hour <= 11) return 1; // Morning
        if (hour <= 17) return 2; // Afternoon
        return 3;                 // Evening
    }
    private static double computeMedian(List<Double> amounts) {
        if (amounts.isEmpty()) return 1.0;
        ArrayList<Double> copy = new ArrayList<>(amounts);
        Collections.sort(copy);
        int n = copy.size();
        return (n % 2 == 1) ? copy.get(n/2) : 0.5 * (copy.get(n/2 - 1) + copy.get(n/2));
    }


    private static double median(List<Double> xs) {
        if (xs.isEmpty()) return 1.0;
        ArrayList<Double> copy = new ArrayList<>(xs);
        Collections.sort(copy);
        int n = copy.size();
        return n % 2 == 1 ? copy.get(n / 2) : 0.5 * (copy.get(n / 2 - 1) + copy.get(n / 2));
    }

    private static double mean(List<Double> xs) {
        if (xs.isEmpty()) return 0.0;
        double s = 0.0; for (double v : xs) s += v; return s / xs.size();
    }

    private static double stdAround(double mean, List<Double> xs) {
        if (xs.size() <= 1) return 1.0;
        double s2 = 0.0; for (double v : xs) { double d = v - mean; s2 += d * d; }
        return max(Math.sqrt(s2 / (xs.size() - 1)), 1.0);
    }

}
