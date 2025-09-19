package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.MetricPointDTO;
import org.caureq.caureqopsboard.api.dto.MetricSummaryDTO;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.repo.MetricRepo;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.DoubleSummaryStatistics;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricQueryService {
    private final AssetRepo assetRepo;
    private final MetricRepo metricRepo;

    public List<MetricPointDTO> latest(String hostname, int limit) {
        var asset = findAsset(hostname);
        var list = metricRepo.findTop200ByAssetOrderByTsDesc(asset);
        var sub = list.stream().limit(Math.max(1, Math.min(limit, 200))).toList();
        return sub.stream().map(m ->
                new MetricPointDTO(m.getTs().atOffset(ZoneOffset.UTC), m.getCpu(), m.getRam(), m.getDisk())
        ).toList();
    }

    public List<MetricPointDTO> between(String hostname, Instant from, Instant to) {
        var asset = findAsset(hostname);
        var list = metricRepo.findByAssetAndTsBetweenOrderByTsAsc(asset, from, to);
        return list.stream().map(m ->
                new MetricPointDTO(m.getTs().atOffset(ZoneOffset.UTC), m.getCpu(), m.getRam(), m.getDisk())
        ).toList();
    }

    public MetricSummaryDTO summary(String hostname, Instant from, Instant to) {
        var pts = between(hostname, from, to);
        var cpuStats = pts.stream().mapToDouble(MetricPointDTO::cpu).summaryStatistics();
        var ramStats = pts.stream().mapToDouble(MetricPointDTO::ram).summaryStatistics();
        var dskStats = pts.stream().mapToDouble(MetricPointDTO::disk).summaryStatistics();
        return new MetricSummaryDTO(
                from.atOffset(ZoneOffset.UTC), to.atOffset(ZoneOffset.UTC),
                safeAvg(cpuStats), safeMax(cpuStats),
                safeAvg(ramStats), safeMax(ramStats),
                safeAvg(dskStats), safeMax(dskStats),
                pts.size()
        );
    }

    private Asset findAsset(String hostname) {
        return assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
    }

    private Double safeAvg(DoubleSummaryStatistics s) { return s.getCount() == 0 ? null : s.getAverage(); }
    private Double safeMax(DoubleSummaryStatistics s) { return s.getCount() == 0 ? null : s.getMax(); }
}
