package org.caureq.caureqopsboard.service.alerts;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.AppProps;
import org.springframework.stereotype.Service;

/**
 * Runtime alert thresholds. Initialized from configuration and updateable via admin API.
 */
@Service
@Slf4j
public class AlertConfigService {
    @Getter private volatile double cpuHighPct = 90.0;
    @Getter private volatile double ramHighPct = 90.0;
    @Getter private volatile double diskHighPct = 95.0;

    public AlertConfigService(AppProps props) {
        if (props.alerts() != null) {
            if (props.alerts().cpuHighPct() != null) cpuHighPct = props.alerts().cpuHighPct();
            if (props.alerts().ramHighPct() != null) ramHighPct = props.alerts().ramHighPct();
            if (props.alerts().diskHighPct() != null) diskHighPct = props.alerts().diskHighPct();
        }
        log.info("[Alerts] thresholds cpu={} ram={} disk={}", cpuHighPct, ramHighPct, diskHighPct);
    }

    public synchronized void update(Double cpu, Double ram, Double disk) {
        if (cpu != null) cpuHighPct = cpu;
        if (ram != null) ramHighPct = ram;
        if (disk != null) diskHighPct = disk;
        log.info("[Alerts] thresholds updated cpu={} ram={} disk={}", cpuHighPct, ramHighPct, diskHighPct);
    }
}

