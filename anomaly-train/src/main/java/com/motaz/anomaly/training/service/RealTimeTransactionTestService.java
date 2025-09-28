package com.motaz.anomaly.training.service;

import com.motaz.anomaly.training.dto.FeatureBaseline;
import com.motaz.anomaly.training.model.*;
import com.motaz.anomaly.training.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeTransactionTestService {

    private static final double THRESHOLD = 0.75;
    private final ModelRegistryRepository modelRegistryRepository;
    private final CustomerBaseLineRepository customerBaseLineRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionFeatureRepository transactionFeatureRepository;
    private final AnomalyAlertRepository  anomalyAlertRepository;

    private IsolationForest loadLatestModel() throws IOException, ClassNotFoundException {
        IsolationForest iForest = null ;
        Optional<ModelRegistryEntity> optionalModelRegistryEntity = modelRegistryRepository.findLatestIFModel();
        if (optionalModelRegistryEntity.isPresent()) {
            ModelRegistryEntity modelRegistryEntity = optionalModelRegistryEntity.get();
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(modelRegistryEntity.getModelBytes()));
            iForest  = (IsolationForest) ois.readObject();
            ois.close();
        }
        return iForest;
    }

    /** Simple hour→segment mapping (0 Night, 1 Morning, 2 Afternoon, 3 Evening). */
    private static int segmentOfHour(int hour) {
        if (hour <= 5)  return 0;
        if (hour <= 11) return 1;
        if (hour <= 17) return 2;
        return 3;
    }

    public void simulateAndScore(long customerId,
                                 double amount,
                                 LocalDateTime tsUtc) throws Exception {
        //Load model
        IsolationForest iForest = loadLatestModel();

        double sTypical  = iForest.score(new double[]{0, 1, 1, 1});
        double sExtreme  = iForest.score(new double[]{10, 20, 20, 20});
        boolean lowerIsWorse = sExtreme < sTypical;
        log.info("IF orientation: typical={} extreme={} → lowerIsWorse={}", sTypical, sExtreme, lowerIsWorse);

        //get customer baseline
        double mean = 0, std = 0, median = 0, segNight = 0, segMorning = 0, segAfternoon = 0, segEvening = 0;
        Optional<CustomerBaselineEntity> customerBaselineEntityOption = customerBaseLineRepository.findById(customerId);
        if (!customerBaselineEntityOption.isPresent()) {
           throw  new Exception("Customer not found");
        }

        CustomerBaselineEntity customerBaselineEntity = customerBaselineEntityOption.get();
        mean = customerBaselineEntity.getMeanAmount();
        std = customerBaselineEntity.getStdAmount();
        median = customerBaselineEntity.getMedianAmount();
        segNight = customerBaselineEntity.getSegMeanNight();
        segMorning = customerBaselineEntity.getSegMeanMorning();
        segAfternoon = customerBaselineEntity.getSegMeanAfternoon();
        segEvening = customerBaselineEntity.getSegMeanEvening();

        //Calculate
        int seg = segmentOfHour(tsUtc.getHour());
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

        //Scoring
        double score = iForest.score(realTimeFeatureSet);
        boolean underReview = score >= THRESHOLD;

        log.info(" custId={} seg={} amount={}  features=[z={} timeSeg={} velocityRatio={} medDev={}] score={} decision={}"
                , customerId, seg, String.format("%.2f", amount),
                String.format("%.3f", amountZScore),
                String.format("%.3f", timeSegmentRatio),
                String.format("%.3f", velocityRatio),
                String.format("%.3f", medianDeviation),
                String.format("%.4f", score),
                underReview ? "UNDER_REVIEW" : "ALLOW");

//        //Save Transaction to T_transaction table
//        TransactionEntity transactionEntity = new TransactionEntity();
//        transactionEntity.setCustomerId(customerId);
//        transactionEntity.setCurrencyCode("SAR");
//        transactionEntity.setAmount(new BigDecimal(amount));
//        transactionEntity.setChannel("ATM");
//        transactionEntity.setTsUtc(tsUtc.toInstant(ZoneOffset.UTC));
//        final TransactionEntity savedTransaction = transactionRepository.save(transactionEntity);
//
//        //Save Transaction to T_transaction_features table
//        TransactionFeatureEntity transactionFeatureEntity = new TransactionFeatureEntity();
//        transactionFeatureEntity.setTxn(savedTransaction);
//        transactionFeatureEntity.setCustomerId(customerId);
//        transactionFeatureEntity.setTsUtc(savedTransaction.getTsUtc());
//        transactionFeatureEntity.setCurrencyCode("SAR");
//        transactionFeatureEntity.setAmount(amount);
//
//        transactionFeatureEntity.setAmountZScore(amountZScore);
//        transactionFeatureEntity.setTimeSegmentRatio(timeSegmentRatio);
//        transactionFeatureEntity.setVelocityRatio(velocityRatio);
//        transactionFeatureEntity.setMedianDeviation(medianDeviation);
//
//
//        FeatureBaseline featureBaseline = new FeatureBaseline();
//        featureBaseline.n = customerBaselineEntity.getNTx()+1;
//        double delta  = amount - featureBaseline.mean;
//        featureBaseline.mean += delta / featureBaseline.n;
//        double delta2 = amount- featureBaseline.mean;
//        featureBaseline.m2 += delta * delta2;
//        featureBaseline.std = (featureBaseline.n > 1) ? sqrt(featureBaseline.m2 / (featureBaseline.n - 1)) : max(featureBaseline.std, 1.0);
//        featureBaseline.segCount[seg] += 1;
//        double prev = featureBaseline.segMean[seg];
//        featureBaseline.segMean[seg] = prev + (amount - prev) / featureBaseline.segCount[seg];
//
//        transactionFeatureEntity.setBaselineN(featureBaseline.n);
//        transactionFeatureEntity.setBaselineMeanAmount(featureBaseline.mean);
//        transactionFeatureEntity.setBaselineStdAmount(featureBaseline.std);
//        transactionFeatureEntity.setBaselineMedianAmount(median);
//        transactionFeatureEntity.setBaselineSegIndex(seg);
//        transactionFeatureEntity.setBaselineSegMean(segMean);
//        transactionFeatureRepository.save(transactionFeatureEntity);
//
//        // Log to Transaction Alert Table
//        AnomalyAlertEntity anomalyAlertEntity = new AnomalyAlertEntity();
//        anomalyAlertEntity.setCustomerId(customerId);
//        anomalyAlertEntity.setTxnId(savedTransaction.getId().toString());
//        anomalyAlertEntity.setAmount(new BigDecimal(amount));
//        anomalyAlertEntity.setCurrencyCode("SAR");
//        anomalyAlertEntity.setChannel("ATM");
//        anomalyAlertEntity.setTsUtc(savedTransaction.getTsUtc());
//        anomalyAlertEntity.setScore(new BigDecimal(score));
//        anomalyAlertEntity.setSeverity("UNDER_REVIEW");
//
//        String featuresJson = String.format(
//                "{\"amountZScore\": %.6f, \"timeSegmentRatio\": %.6f, \"velocityRatio\": %.6f, \"medianDeviation\": %.6f}",
//                amountZScore, timeSegmentRatio, velocityRatio, medianDeviation
//        );
//        String baselineJson = String.format(
//                "{\"mean\": %.6f, \"std\": %.6f, \"median\": %.6f, \"segmentIndex\": %d, \"segmentMean\": %.6f}",
//                mean, std, median, seg, segMean
//        );
//        Map<String,Object> factsJson = new HashMap<>();
//        factsJson.put("features", featuresJson);
//        factsJson.put("baseline", baselineJson);
//        anomalyAlertEntity.setFactsJson(factsJson);
//
//        anomalyAlertRepository.save(anomalyAlertEntity);

    }
    static double clip(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

//    // --- recompute & upsert the customer's 90d baseline (Java-only)
//    private void updateCustomerBaseline90d(long customerId, Connection cx) throws Exception {
//        var windowEnd   = java.time.LocalDateTime.now(ZoneOffset.UTC);
//        var windowStart = windowEnd.minusDays(90);
//
//        List<Double> all = new ArrayList<>();
//        List<Double> night = new ArrayList<>(), morning = new ArrayList<>(),
//                afternoon = new ArrayList<>(), evening = new ArrayList<>();
//
//        try (PreparedStatement ps = cx.prepareStatement("""
//            SELECT amount_sar, ts_utc
//            FROM transactions_raw
//            WHERE customer_id = ? AND ts_utc >= ? AND ts_utc < ?
//            ORDER BY ts_utc
//        """)) {
//            ps.setLong(1, customerId);
//            ps.setTimestamp(2, Timestamp.valueOf(windowStart));
//            ps.setTimestamp(3, Timestamp.valueOf(windowEnd));
//            try (ResultSet rs = ps.executeQuery()) {
//                while (rs.next()) {
//                    double amt = rs.getBigDecimal(1).doubleValue();
//                    int hour = rs.getTimestamp(2).toLocalDateTime().getHour();
//                    int seg = segmentOfHour(hour);
//                    all.add(amt);
//                    switch (seg) {
//                        case 0 -> night.add(amt);
//                        case 1 -> morning.add(amt);
//                        case 2 -> afternoon.add(amt);
//                        default -> evening.add(amt);
//                    }
//                }
//            }
//        }
//
//        if (all.isEmpty()) {
//            log.warn("No txns in last 90d for customer {}", customerId);
//            return;
//        }
//
//        // mean/std/median (std with sample variance)
//        double sum = 0, sumsq = 0;
//        for (double v : all) { sum += v; sumsq += v*v; }
//        int n = all.size();
//        double mean = sum / n;
//        double var  = (n > 1) ? Math.max((sumsq / n) - mean*mean, 0.0) * n/(n-1.0) : 1.0;
//        double std  = Math.max(Math.sqrt(var), 1.0);
//        double med  = median(all);
//
//        // segment means; fallback to overall mean if a bucket is empty
//        double segNight    = night.isEmpty()     ? mean : night.stream().mapToDouble(d->d).average().orElse(mean);
//        double segMorning  = morning.isEmpty()   ? mean : morning.stream().mapToDouble(d->d).average().orElse(mean);
//        double segAfternoon= afternoon.isEmpty() ? mean : afternoon.stream().mapToDouble(d->d).average().orElse(mean);
//        double segEvening  = evening.isEmpty()   ? mean : evening.stream().mapToDouble(d->d).average().orElse(mean);
//
//        try (PreparedStatement upsert = cx.prepareStatement("""
//            INSERT INTO customer_baseline_90d(
//              customer_id, window_start_utc, window_end_utc, n_tx,
//              mean_amount, std_amount, median_amount,
//              seg_mean_night, seg_mean_morning, seg_mean_afternoon, seg_mean_evening
//            ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
//            ON CONFLICT (customer_id) DO UPDATE SET
//              window_start_utc = EXCLUDED.window_start_utc,
//              window_end_utc   = EXCLUDED.window_end_utc,
//              n_tx             = EXCLUDED.n_tx,
//              mean_amount      = EXCLUDED.mean_amount,
//              std_amount       = EXCLUDED.std_amount,
//              median_amount    = EXCLUDED.median_amount,
//              seg_mean_night   = EXCLUDED.seg_mean_night,
//              seg_mean_morning = EXCLUDED.seg_mean_morning,
//              seg_mean_afternoon = EXCLUDED.seg_mean_afternoon,
//              seg_mean_evening = EXCLUDED.seg_mean_evening,
//              updated_at = now()
//        """)) {
//            upsert.setLong(1, customerId);
//            upsert.setTimestamp(2, Timestamp.valueOf(windowStart));
//            upsert.setTimestamp(3, Timestamp.valueOf(windowEnd));
//            upsert.setInt(4, n);
//            upsert.setDouble(5, mean);
//            upsert.setDouble(6, std);
//            upsert.setDouble(7, med);
//            upsert.setDouble(8, segNight);
//            upsert.setDouble(9, segMorning);
//            upsert.setDouble(10, segAfternoon);
//            upsert.setDouble(11, segEvening);
//            upsert.executeUpdate();
//        }
//
//        log.info("Baseline 90d refreshed for customer {} (n={})", customerId, n);
//    }

}
