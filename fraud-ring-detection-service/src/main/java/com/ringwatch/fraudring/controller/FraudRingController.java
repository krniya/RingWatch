package com.ringwatch.fraudring.controller;

import com.ringwatch.fraudring.controller.dto.FraudRingResponse;
import com.ringwatch.fraudring.service.FraudRingDetectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FraudRingController {

    private final FraudRingDetectionService fraudRingDetectionService;

    public FraudRingController(FraudRingDetectionService fraudRingDetectionService) {
        this.fraudRingDetectionService = fraudRingDetectionService;
    }

    @GetMapping("/fraud-rings")
    public List<FraudRingResponse> list() {
        return fraudRingDetectionService.findRecentDetections().stream()
                .map(FraudRingResponse::from)
                .toList();
    }
}
