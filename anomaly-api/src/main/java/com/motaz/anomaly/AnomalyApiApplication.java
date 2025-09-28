package com.motaz.anomaly;

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableRedisDocumentRepositories
public class AnomalyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnomalyApiApplication.class, args);
    }

}
