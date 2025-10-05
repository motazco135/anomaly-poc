package com.motaz.anomaly.training;

import com.motaz.anomaly.training.repository.*;
import com.motaz.anomaly.training.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@Slf4j
@SpringBootApplication
public class AnomalyModelTrainingApplication{

    public static void main(String[] args) {
        SpringApplication.run(AnomalyModelTrainingApplication.class, args);
    }


    @Bean
    public CommandLineRunner initDatabase(TransactionRepository transactionRepository,
                                          TransactionFeatureRepository transactionFeatureRepository,
                                          CustomerBaseLineRepository customerBaseLineRepository,
                                          ModelRegistryRepository modelRegistryRepository,
                                          AnomalyAlertRepository anomalyAlertRepository) {
        return args -> {
            System.out.println("Initializing database with employee data...");
            log.info("Initializing database...");
            DataPreparationService dataPreparationService = new DataPreparationService(transactionRepository);
            dataPreparationService.prepareData();
            log.info("Initializing database Completed...");

            log.info("Fill Anomaly Feature Data...");
            AnomalyFeatureFillService anomalyFeatureFillService = new AnomalyFeatureFillService(transactionRepository,transactionFeatureRepository,customerBaseLineRepository);
            anomalyFeatureFillService.doFeatureFill();
            log.info("Fill Anomaly Feature Data Completed...");

            log.info("Train Isolation Forest Model...");
            TrainIsolationForestService trainIsolationForestService = new TrainIsolationForestService(transactionFeatureRepository,modelRegistryRepository);
            trainIsolationForestService.trainModel();
            log.info("Train Isolation Forest Model Completed...");

            log.info("Isolation Forest Visualization...");
            ModelVizService modelVizService = new ModelVizService(modelRegistryRepository,transactionFeatureRepository);
            modelVizService.visualize();
            log.info("Isolation Forest Visualization Completed...");

            log.info("Real Time Transaction Test...");
            RealTimeTransactionTestService realTimeTransactionTestService = new RealTimeTransactionTestService(modelRegistryRepository,
                    customerBaseLineRepository,transactionRepository,transactionFeatureRepository,anomalyAlertRepository);

            long customerId = 101L;
            double amountSar = 10000;
            LocalDateTime tsUtc = LocalDateTime.now();
            realTimeTransactionTestService.simulateAndScore(customerId,amountSar,tsUtc);
            log.info("Real Time Transaction Test Completed...");

        };
    }
}
