package com.motaz.anomaly.training;

import smile.anomaly.IsolationForest;

import java.util.Arrays;
import java.util.Random;

public class IsolationForestExample {

    public static void main(String[] args) {
        // 1. Generate a synthetic dataset with normal and anomalous points
        double[][] data = generateData();

        // 2. Train the Isolation Forest model using the static `fit()` method
        System.out.println("Training Isolation Forest model...");
        IsolationForest forest = IsolationForest.fit(data);
        System.out.println("Model trained successfully.");

        // 3. Score all data points and identify anomalies
        double[] anomalyScores = forest.score(data);

        // Print results and identify anomalies based on a threshold
        System.out.println("\nAnomaly Scores:");
        for (int i = 0; i < data.length; i++) {
            // A score closer to 1 indicates a higher likelihood of being an anomaly.
            if (anomalyScores[i] > 0.6) {
                System.out.printf("Data point %s is an anomaly with score %.4f%n",
                        Arrays.toString(data[i]), anomalyScores[i]);
            } else {
                System.out.printf("Data point %s is normal with score %.4f%n",
                        Arrays.toString(data[i]), anomalyScores[i]);
            }
        }
    }

    /**
     * Generates a dataset with two clusters of normal data and some random outliers.
     */
    private static double[][] generateData() {
        Random rand = new Random(0);
        int n_normal = 200;
        int n_anomalies = 10;
        double[][] data = new double[n_normal + n_anomalies][2];

        // Generate two clusters of normal points
        for (int i = 0; i < n_normal / 2; i++) {
            data[i][0] = 5 * rand.nextGaussian() + 10;
            data[i][1] = 5 * rand.nextGaussian() + 10;
        }
        for (int i = n_normal / 2; i < n_normal; i++) {
            data[i][0] = 5 * rand.nextGaussian() - 10;
            data[i][1] = 5 * rand.nextGaussian() - 10;
        }

        // Generate anomalous points
        for (int i = n_normal; i < n_normal + n_anomalies; i++) {
            data[i][0] = 25 * rand.nextGaussian();
            data[i][1] = 25 * rand.nextGaussian();
        }

        return data;
    }
}