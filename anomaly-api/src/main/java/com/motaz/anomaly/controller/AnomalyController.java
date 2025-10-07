package com.motaz.anomaly.controller;

import com.motaz.anomaly.dto.AnomalyAlertDto;
import com.motaz.anomaly.dto.FundTransferRequestDto;
import com.motaz.anomaly.dto.FundTransferResponseDto;
import com.motaz.anomaly.services.AnomalyAlertService;
import com.motaz.anomaly.services.AnomalyScoringService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly")
@RequiredArgsConstructor
public class AnomalyController {
    private final AnomalyScoringService scoringService;
    private final AnomalyAlertService alertService;

    @PostMapping("/score")
    public FundTransferResponseDto score(@RequestBody FundTransferRequestDto req) throws IOException, ClassNotFoundException {
        return scoringService.score(req);
    }

    @GetMapping("/alerts/{customerId}")
    public List<AnomalyAlertDto> getAllAlerts(
            @Parameter(description = "The unique identifier of the customer", required = true, example = "101")
            @PathVariable(name = "customerId") Long customerId ){
        return  alertService.findAllByCustomerId(customerId);
    }
}