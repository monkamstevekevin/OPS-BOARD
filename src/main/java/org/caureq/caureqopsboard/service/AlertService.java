package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.AppProps;
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

    public void evaluateOnIngest(Asset asset, Metric metric) {
        var status = computeStatus(asset.getLastSeen(),
                props.status().upMinutes(), props.status().staleMinutes());
        if ("DOWN".equals(status)) {
            log.warn("ALERT DOWN: {}", asset.getHostname());
        } else if ("STALE".equals(status)) {
            log.warn("ALERT STALE: {}", asset.getHostname());
        }
        if (metric.getCpu() > 90.0) {
            log.warn("ALERT HIGH CPU: {} cpu={}%", asset.getHostname(), metric.getCpu());
        }
    }

    static String computeStatus(Instant lastSeen, int upMin, int staleMin){
        if (lastSeen == null) return "DOWN";
        var minutes = Duration.between(lastSeen, Instant.now()).toMinutes();
        if (minutes <= upMin)   return "UP";
        if (minutes <= staleMin) return "STALE";
        return "DOWN";
    }
}
