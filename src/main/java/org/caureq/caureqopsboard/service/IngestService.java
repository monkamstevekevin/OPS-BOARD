package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.api.dto.IngestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.domain.Metric;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.repo.MetricRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {
    private final AssetRepo assetRepo;
    private final MetricRepo metricRepo;
    private final AlertService alertService; // simple log en v1
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void ingest(IngestDTO d) {
        var hostname = d.hostname().trim();
        var ip = d.ip().trim();
        var os = (d.os() == null) ? "" : d.os().trim();

        var asset = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseGet(() -> {
                    var a = new Asset();
                    a.setHostname(hostname);
                    return a;
                });

        // IP v1: do not overwrite with invalid/placeholder IPv4
        if (isUsableIpv4(ip)) {
            asset.setIp(ip);
        }
        asset.setOs(os);
        asset.setLastSeen(Instant.now());
        assetRepo.save(asset);

        String servicesJson;
        try { servicesJson = objectMapper.writeValueAsString(d.services()); }
        catch (Exception e) { servicesJson = String.valueOf(d.services()); }

        var m = Metric.builder()
                .asset(asset)
                .cpu(d.cpu())
                .ram(d.ram())
                .disk(d.disk())
                .services(servicesJson)
                .ts(Instant.now())
                .build();
        metricRepo.save(m);

        alertService.evaluateOnIngest(asset, m);
        log.debug("ingested {} cpu={} ram={} disk={}", hostname, d.cpu(), d.ram(), d.disk());
    }

    private boolean isUsableIpv4(String ip){
        if (ip == null) return false;
        var s = ip.trim();
        if (s.isEmpty()) return false;
        if ("0.0.0.0".equals(s)) return false;
        if (s.startsWith("127.")) return false;
        if (s.startsWith("169.254.")) return false; // APIPA
        return s.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    }
}
