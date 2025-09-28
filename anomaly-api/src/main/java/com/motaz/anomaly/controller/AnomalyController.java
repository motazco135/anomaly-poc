package com.motaz.anomaly.controller;

import com.motaz.anomaly.dto.FundTransferRequestDto;
import com.motaz.anomaly.dto.FundTransferResponseDto;
import com.motaz.anomaly.services.AnomalyScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/anomaly")
@RequiredArgsConstructor
public class AnomalyController {
    private final AnomalyScoringService scoringService;

    @PostMapping("/score")
    public FundTransferResponseDto score(@RequestBody FundTransferRequestDto req) throws IOException, ClassNotFoundException {
        return scoringService.score(req);
    }
}