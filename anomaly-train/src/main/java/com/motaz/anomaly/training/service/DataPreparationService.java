package com.motaz.anomaly.training.service;

import com.motaz.anomaly.training.model.TransactionEntity;
import com.motaz.anomaly.training.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataPreparationService {

    private final TransactionRepository transactionRepository;

    Random rnd = new Random(42);
    int customers = 500;
    int txnsPer = 120; // ~90 days
    String[] channels = {"POS","ATM","ONLINE","WIRE"};

    public void prepareData() {
        LocalDate start = LocalDate.now().minusDays(90);
        List<TransactionEntity> transactionEntityList = new ArrayList<TransactionEntity>();
        for (int c=1;c<=customers;c++) {
            double mean = 3000 + rnd.nextDouble()*4000;  // 3k..7k
            double std  = 600 + rnd.nextDouble()*1000;   // 600..1600
            for (int i=0;i<txnsPer;i++) {
                LocalDateTime ts = start.plusDays(rnd.nextInt(90))
                        .atTime(rnd.nextInt(24), rnd.nextInt(60));
                double amt = Math.max(50, mean + rnd.nextGaussian()*std);
                // inject 5% spikes at night
                if (rnd.nextDouble()<0.05 && ts.getHour()<6) {
                    amt *= (5 + rnd.nextDouble()*8); // 5x..13x
                }
                TransactionEntity transactionEntity = new TransactionEntity();
                transactionEntity.setCustomerId(Long.valueOf(c));
                transactionEntity.setAmount(BigDecimal.valueOf(Math.round(amt*100.0)/100.0));
                transactionEntity.setCurrencyCode("SAR");
                transactionEntity.setChannel(channels[rnd.nextInt(channels.length)]);
                transactionEntity.setTsUtc(Timestamp.valueOf(ts).toInstant());
                transactionEntityList.add(transactionEntity);
            }
        }
        transactionRepository.saveAll(transactionEntityList);
    }
}
