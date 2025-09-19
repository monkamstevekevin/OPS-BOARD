package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.api.dto.IngestDTO;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.domain.Metric;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.repo.MetricRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {
    private final AssetRepo assetRepo;
    private final MetricRepo metricRepo;
    private final AlertService alertService; // simple log en v1

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

        // source de vérité simple
        asset.setIp(ip);
        asset.setOs(os);
        asset.setLastSeen(Instant.now());
        assetRepo.save(asset);

        var m = Metric.builder()
                .asset(asset)
                .cpu(d.cpu())
                .ram(d.ram())
                .disk(d.disk())
                .services(d.services().toString())
                .ts(Instant.now())
                .build();
        metricRepo.save(m);

        alertService.evaluateOnIngest(asset, m);
        log.debug("ingested {} cpu={} ram={} disk={}", hostname, d.cpu(), d.ram(), d.disk());
    }
}
