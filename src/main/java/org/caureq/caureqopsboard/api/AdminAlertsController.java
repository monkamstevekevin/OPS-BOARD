package org.caureq.caureqopsboard.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.service.alerts.AlertConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin endpoints to view/update alert thresholds at runtime. */
@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
public class AdminAlertsController {
    private final AlertConfigService cfg;

    @GetMapping("/config")
    public Map<String, Object> get() {
        return Map.of(
                "cpuHighPct", cfg.getCpuHighPct(),
                "ramHighPct", cfg.getRamHighPct(),
                "diskHighPct", cfg.getDiskHighPct()
        );
    }

    public record UpdateReq(Double cpuHighPct, Double ramHighPct, Double diskHighPct) {}

    @PutMapping("/config")
    public ResponseEntity<?> update(@RequestBody @Valid UpdateReq body) {
        cfg.update(body.cpuHighPct(), body.ramHighPct(), body.diskHighPct());
        return ResponseEntity.noContent().build();
    }
}

