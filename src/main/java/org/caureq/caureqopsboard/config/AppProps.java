package org.caureq.caureqopsboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProps(String apiKey, StatusProps status, String defaultNode, AlertsProps alerts,
                       String localHosts) {
    /** Status thresholds and UI hints */
    public record StatusProps(int upMinutes, int staleMinutes) {}
    /** Basic alerting thresholds (optional; can be null) */
    public record AlertsProps(Double cpuHighPct, Double ramHighPct, Double diskHighPct) {}
}
