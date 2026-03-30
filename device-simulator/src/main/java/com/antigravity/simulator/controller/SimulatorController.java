package com.antigravity.simulator.controller;

import com.antigravity.simulator.service.DeviceSimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final DeviceSimulatorService simulatorService;

    public SimulatorController(DeviceSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startSimulation() {
        simulatorService.startSimulation();
        return ResponseEntity.ok("Simulation started via Virtual Threads.\n");
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopSimulation() {
        simulatorService.stopSimulation();
        return ResponseEntity.ok("Simulation sequence halted.\n");
    }
}
