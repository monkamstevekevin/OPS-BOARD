package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.AppProps;
import org.caureq.caureqopsboard.service.alerts.AlertRegistry;
import org.caureq.caureqopsboard.service.alerts.AlertConfigService;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.domain.Metric;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {
    private final AppProps props;
    private final AlertRegistry registry;
    private final AlertConfigService cfg;
    private final DiagnosticService diag;

    public void evaluateOnIngest(Asset asset, Metric metric) {
        var status = computeStatus(asset.getLastSeen(),
                props.status().upMinutes(), props.status().staleMinutes());
        if ("DOWN".equals(status)) {
            log.warn("ALERT DOWN: {}", asset.getHostname());
        } else if ("STALE".equals(status)) {
            log.warn("ALERT STALE: {}", asset.getHostname());
        }
        double cpuTh = cfg.getCpuHighPct();
        double ramTh = cfg.getRamHighPct();
        double dskTh = cfg.getDiskHighPct();
        if (metric.getCpu() > cpuTh) {
            var extra = safeTop(asset);
            var msg = "CPU high: %.1f%% > %.1f%%%s".formatted(metric.getCpu(), cpuTh, extra);
            registry.add(new AlertRegistry.Alert(java.util.UUID.randomUUID().toString(), asset.getHostname(), "CPU_HIGH", msg, java.time.Instant.now(), false));
            log.warn("ALERT HIGH CPU: {} cpu={}{}", asset.getHostname(), metric.getCpu(), extra);
        }
        if (metric.getRam() > ramTh) {
            var extra = safeTop(asset);
            var msg = "RAM high: %.1f%% > %.1f%%%s".formatted(metric.getRam(), ramTh, extra);
            registry.add(new AlertRegistry.Alert(java.util.UUID.randomUUID().toString(), asset.getHostname(), "RAM_HIGH", msg, java.time.Instant.now(), false));
            log.warn("ALERT HIGH RAM: {} ram={}{}", asset.getHostname(), metric.getRam(), extra);
        }
        if (metric.getDisk() > dskTh) {
            var msg = "Disk high: %.1f%% > %.1f%%".formatted(metric.getDisk(), dskTh);
            registry.add(new AlertRegistry.Alert(java.util.UUID.randomUUID().toString(), asset.getHostname(), "DISK_HIGH", msg, java.time.Instant.now(), false));
            log.warn("ALERT HIGH DISK: {} disk={}%", asset.getHostname(), metric.getDisk());
        }
    }

    private String safeTop(Asset asset) {
        try {
            String s = diag.captureTopProcess(asset);
            if (s != null && !s.isBlank()) {
                if (s.length() > 120) s = s.substring(0, 120);
                return " [top=" + s + "]";
            }
        } catch (Exception ignored) {}
        return "";
    }

    static String computeStatus(Instant lastSeen, int upMin, int staleMin){
        if (lastSeen == null) return "DOWN";
        var minutes = Duration.between(lastSeen, Instant.now()).toMinutes();
        if (minutes <= upMin)   return "UP";
        if (minutes <= staleMin) return "STALE";
        return "DOWN";
    }
}
