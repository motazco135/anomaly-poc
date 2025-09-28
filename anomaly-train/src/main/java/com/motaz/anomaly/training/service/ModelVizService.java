package com.motaz.anomaly.training.service;


import com.motaz.anomaly.training.model.ModelRegistryEntity;
import com.motaz.anomaly.training.model.TransactionFeatureEntity;
import com.motaz.anomaly.training.repository.ModelRegistryRepository;
import com.motaz.anomaly.training.repository.TransactionFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;
import smile.plot.swing.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelVizService {

    private static final double THRESHOLD = 0.75;
    private static final Long CUSTOMER_ID = 101L;
    private final ModelRegistryRepository modelRegistryRepository;
    private final TransactionFeatureRepository transactionFeatureRepository;

    public void visualize() throws Exception{
        List<TransactionFeatureEntity> transactionFeatureEntityList = transactionFeatureRepository.findByCustomerIdAndIsTrainable(CUSTOMER_ID,true);
        List<double[]> rows = new ArrayList<>();
        List<Double> amountZscoreList   = new ArrayList<>();
        List<Double> timeSegmentRatioList = new ArrayList<>();
        List<Double> velocityRatioList = new ArrayList<>();
        List<Double> medianDeviationList = new ArrayList<>();
        List<Double> scoreList = new ArrayList<>();
        transactionFeatureEntityList.forEach(transactionFeatureEntity -> {
            amountZscoreList.add(transactionFeatureEntity.getAmountZScore());
            timeSegmentRatioList.add(transactionFeatureEntity.getTimeSegmentRatio());
            velocityRatioList.add(transactionFeatureEntity.getVelocityRatio());
            medianDeviationList.add(transactionFeatureEntity.getMedianDeviation());
            rows.add(new double[]{
                    transactionFeatureEntity.getAmountZScore(),
                    transactionFeatureEntity.getTimeSegmentRatio(),
                    transactionFeatureEntity.getVelocityRatio(),
                    transactionFeatureEntity.getMedianDeviation()
            });
        });

        IsolationForest ifModel = loadLatestModel();
        double[] scores = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            scores[i] = ifModel.score(rows.get(i));
            scoreList.add(scores[i]);
        }

        saveHistogram("hist_amount_z.png", "Histogram • Amount Z-Score",
                "amount_z_score", "frequency", amountZscoreList);

        saveHistogram("hist_time_segment_ratio.png", "Histogram • Time Segment Ratio",
                "time_segment_ratio", "frequency", timeSegmentRatioList);
        saveHistogram("hist_velocity_ratio.png", "Histogram • Velocity Ratio",
                "velocity_ratio", "frequency", velocityRatioList);
        saveHistogram("hist_median_deviation.png", "Histogram • Median Deviation",
                "median_deviation", "frequency", medianDeviationList);
        saveHistogram("hist_anomaly_score.png", "Histogram • Isolation Forest Score",
                "isolation_forest_score", "frequency", scoreList);

        saveScoreColoredScatter("scatter_z_vs_velocity_by_score.png",
                "Scatter • Amount Z-Score (X) vs Velocity Ratio (Y) • colored by IF score",
                "amount_z_score (X)", "velocity_ratio (Y)",
                amountZscoreList, velocityRatioList);


    }

    private void saveHistogram(String fileName, String title,
                               String xLabel, String yLabel,
                               List<Double> data) throws Exception{
        var plot = Histogram.of(toPrimitive(data));
        Figure canvas = plot.figure();
        canvas.setTitle(title);
        canvas.setAxisLabels(xLabel, yLabel);
        saveToFile(canvas, fileName);
    }

    private static void saveScoreColoredScatter(String fileName, String title,
                                                String xLabel, String yLabel,
                                                List<Double> x, List<Double> y) throws Exception {

        // 2D points
        double[][] xy = new double[x.size()][2];
        for (int i = 0; i < x.size(); i++) {
            xy[i][0] = x.get(i); // X axis
            xy[i][1] = y.get(i); // Y axis
        }

        var sp = ScatterPlot.of(xy,Color.red);
        Figure canvas = sp.figure();
        canvas.setTitle(title);
        canvas.setAxisLabels(xLabel, yLabel);
        saveToFile(canvas, fileName);
    }

    private static void saveToFile(Figure canvas, String fileName) throws Exception {
        int width = 1200;
        int height = 800;
        BufferedImage img = canvas.toBufferedImage(width, height);
        File out = new File("/Users/motaz/Work/my-projects/anomaly-model-training/src/main/resources/", fileName);
        ImageIO.write(img, "png", out);
        System.out.println("Saved: " + out.getAbsolutePath());
    }

    private static double[] toPrimitive(List<Double> src) {
        double[] a = new double[src.size()];
        for (int i = 0; i < src.size(); i++) a[i] = src.get(i);
        return a;
    }

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
}
