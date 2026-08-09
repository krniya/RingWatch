package com.ringwatch.fraudring.repository;

import com.ringwatch.fraudring.model.FraudRingDetection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudRingDetectionRepository extends JpaRepository<FraudRingDetection, UUID> {

    List<FraudRingDetection> findAllByOrderByDetectedAtDesc();
}
