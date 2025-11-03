package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.MetricPointDTO;
import org.caureq.caureqopsboard.api.dto.MetricSummaryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper om = new ObjectMapper();

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

    public java.util.Map<String, String> latestServices(String hostname) {
        var asset = findAsset(hostname);
        var m = metricRepo.findTopByAssetOrderByTsDesc(asset);
        if (m == null || m.getServices() == null || m.getServices().isBlank()) return java.util.Map.of();
        String s = m.getServices().trim();
        // Try JSON first
        try {
            var type = om.getTypeFactory().constructMapType(java.util.Map.class, String.class, String.class);
            return om.readValue(s, type);
        } catch (Exception ignore) {}
        // Fallback legacy format: {k=v, k2=v2}
        try {
            String t = s;
            if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length()-1);
            java.util.Map<String,String> map = new java.util.HashMap<>();
            for (String part : t.split(",")) {
                var kv = part.trim().split("=", 2);
                if (kv.length==2) map.put(kv[0].trim(), kv[1].trim());
            }
            return map;
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }

    private Asset findAsset(String hostname) {
        return assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
    }

    private Double safeAvg(DoubleSummaryStatistics s) { return s.getCount() == 0 ? null : s.getAverage(); }
    private Double safeMax(DoubleSummaryStatistics s) { return s.getCount() == 0 ? null : s.getMax(); }
}
