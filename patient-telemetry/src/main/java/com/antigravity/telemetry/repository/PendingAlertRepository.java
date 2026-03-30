package com.antigravity.telemetry.repository;

import com.antigravity.telemetry.domain.PendingAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingAlertRepository extends JpaRepository<PendingAlert, String> {
}
