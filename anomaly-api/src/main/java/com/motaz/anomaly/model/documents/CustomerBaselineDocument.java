package com.motaz.anomaly.model.documents;

import com.redis.om.spring.annotations.Document;
import com.redis.om.spring.annotations.Indexed;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Document(value = "customer:baseline",indexName = "CustomerBaseLineIdx")
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBaselineDocument {

    @Id
    private String id;                 // "cust:101"

    @Indexed
    private Long customerId;
    private Double meanAmount;
    private Double stdAmount;
    private Double medianAmount;

    private Double segMeanNight;
    private Double segMeanMorning;
    private Double segMeanAfternoon;
    private Double segMeanEvening;

}
