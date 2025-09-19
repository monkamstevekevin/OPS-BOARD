package org.caureq.caureqopsboard.service;


import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.AssetDetailDTO;
import org.caureq.caureqopsboard.api.dto.AssetListItemDTO;
import org.caureq.caureqopsboard.config.AppProps;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.repo.MetricRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AssetQueryService {
    private final AssetRepo assetRepo;
    private final MetricRepo metricRepo;
    private final AppProps props;

    public List<AssetListItemDTO> list(String q){
        var all = (q==null || q.isBlank()) ? assetRepo.findAll()
                : assetRepo.findAll().stream()
                .filter(a -> a.getHostname()!=null && a.getHostname().toLowerCase().contains(q.toLowerCase()))
                .toList();

        return all.stream().map(a -> {
            var last = metricRepo.findTopByAssetOrderByTsDesc(a);
            var status = computeStatus(a.getLastSeen(), props.status().upMinutes(), props.status().staleMinutes());
            var lastSeenLocal = (a.getLastSeen() == null) ? null
                    : a.getLastSeen().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            return new AssetListItemDTO(
                    a.getHostname(), a.getIp(), a.getOs(),
                    status, a.getLastSeen().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                    last!=null? last.getCpu():null,
                    last!=null? last.getRam():null,
                    last!=null? last.getDisk():null
            );
        }).toList();
    }
    public AssetDetailDTO get(String hostname){
        var a = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
        var tags = (a.getTags()==null || a.getTags().isBlank()) ? List.<String>of()
                : java.util.Arrays.stream(a.getTags().split(",")).toList();

        // ici tu remplaces a.getLastSeen() direct par la version convertie :
        var lastSeenLocal = (a.getLastSeen() == null) ? null
                : a.getLastSeen().atZone(ZoneId.systemDefault()).toOffsetDateTime();

        return new AssetDetailDTO(
                a.getHostname(), a.getIp(), a.getOs(),
                a.getOwner(), tags, lastSeenLocal
        );
    }
    static String computeStatus(Instant lastSeen, int upMin, int staleMin){
        if (lastSeen == null) return "DOWN";
        var minutes = Duration.between(lastSeen, Instant.now()).toMinutes();
        if (minutes <= upMin)   return "UP";
        if (minutes <= staleMin) return "STALE";
        return "DOWN";
    }
}
