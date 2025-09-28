package com.motaz.anomaly.config;

import com.motaz.anomaly.services.CustomerBaselineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LoadCustomerBaseLineConfig {

    private final CustomerBaselineService customerBaselineDocumentService;

    @Bean
    ApplicationRunner initApplicationRunner() {
        return args -> {
            customerBaselineDocumentService.cashCustomerBaseLine();
        };
    }

}
