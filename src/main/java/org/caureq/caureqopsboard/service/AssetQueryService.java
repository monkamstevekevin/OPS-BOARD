package org.caureq.caureqopsboard.service;


import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.AssetDetailDTO;
import org.caureq.caureqopsboard.api.dto.AssetListItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.caureq.caureqopsboard.config.AppProps;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.repo.MetricRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Read side for Assets.
 *
 * Responsibilities
 * - Query assets from the DB with optional search/pagination.
 * - Compute coarse status (UP/STALE/DOWN) from lastSeen windows only.
 * - Batch-load latest metrics for the current page to avoid N+1 queries.
 *
 * Patterns
 * - Repository pattern via Spring Data JPA.
 * - CQRS-inspired: reads avoid calling Proxmox; live state comes from LiveStatusService.
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {
    private final AssetRepo assetRepo;
    private final MetricRepo metricRepo;
    private final AppProps props;
    private final ProxmoxClient proxmox;

    /**
     * Page through assets (DB-backed) and attach latest metrics when available.
     * No Proxmox calls here: live VM state is handled elsewhere.
     */
    public List<AssetListItemDTO> list(String q, Integer limit, Integer offset, boolean includeRetired){
        int size = (limit==null || limit<=0 || limit>1000) ? 200 : limit;
        int off = (offset==null || offset<0) ? 0 : offset;
        int page = off / size;
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("hostname").ascending());

        var pageRes = (q==null || q.isBlank())
                ? assetRepo.findAll(pageable)
                : assetRepo.findByHostnameContainingIgnoreCase(q, pageable);

        var assets = pageRes.getContent();
        if (!includeRetired) {
            assets = assets.stream().filter(a -> {
                var tags = a.getTags();
                if (tags == null || tags.isBlank()) return true;
                // naive CSV check, case-insensitive exact token match
                for (var t : tags.split(",")) if ("retired".equalsIgnoreCase(t.trim())) return false;
                return true;
            }).toList();
        }
        // Batch load latest metrics for visible page to avoid N queries
        var latestById = new java.util.HashMap<Long, org.caureq.caureqopsboard.domain.Metric>();
        try {
            var latest = metricRepo.findLatestForAssets(assets);
            for (var m : latest) {
                if (m.getAsset()!=null && m.getAsset().getId()!=null) latestById.put(m.getAsset().getId(), m);
            }
        } catch (Exception ignored) {}

        return assets.stream().map(a -> {
            var last = latestById.get(a.getId());
            var lastSeenLocal = (a.getLastSeen() == null) ? null
                    : a.getLastSeen().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            var status = computeStatus(a.getLastSeen(), props.status().upMinutes(), props.status().staleMinutes());
            return new AssetListItemDTO(
                    a.getHostname(), a.getIp(), a.getOs(),
                    status, lastSeenLocal,
                    last!=null? last.getCpu():null,
                    last!=null? last.getRam():null,
                    last!=null? last.getDisk():null
            );
        }).toList();
    }
    /**
     * Get a single asset details from DB (owner, tags, lastSeen...).
     * Live VM state is expected to be fetched via LiveStatusService by the UI.
     */
    public AssetDetailDTO get(String hostname){
        var a = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
        var tags = (a.getTags()==null || a.getTags().isBlank()) ? List.<String>of()
                : java.util.Arrays.stream(a.getTags().split(",")).toList();

        var lastSeenLocal = (a.getLastSeen() == null) ? null
                : a.getLastSeen().atZone(ZoneId.systemDefault()).toOffsetDateTime();

        // Keep this endpoint fast: return DB fields only; UI overlays live data from LiveStatusService
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

    private VmInfo enrichFromProxmox(org.caureq.caureqopsboard.domain.Asset a) {
        try {
            Integer vmid = a.getVmid();
            String node = a.getNode();
            if (vmid == null) {
                var m = Pattern.compile("^vm-(\\d+)-").matcher(a.getHostname()==null? "" : a.getHostname());
                if (m.find()) vmid = Integer.parseInt(m.group(1));
            }
            if (node == null || node.isBlank()) {
                node = (props.defaultNode()==null || props.defaultNode().isBlank()) ? "Caureqlab" : props.defaultNode();
            }
            if (vmid == null || node == null || node.isBlank()) {
                var st = computeStatus(a.getLastSeen(), props.status().upMinutes(), props.status().staleMinutes());
                return new VmInfo(a.getIp(), st);
            }

            var cur = proxmox.vmCurrentStatus(node, vmid);
            var running = "running".equalsIgnoreCase(cur.path("status").asText());

            String liveIp = a.getIp();
            try {
                var ifs = proxmox.agentNetworkGetInterfaces(node, vmid);
                var parsed = pickBestIpv4(ifs);
                if (parsed != null && !parsed.isBlank()) liveIp = parsed;
            } catch (Exception ignore) {}

            String finalStatus = running ? "UP" : "DOWN";
            return new VmInfo(liveIp, finalStatus);
        } catch (Exception e) {
            var st = computeStatus(a.getLastSeen(), props.status().upMinutes(), props.status().staleMinutes());
            return new VmInfo(a.getIp(), st);
        }
    }

    private String pickBestIpv4(JsonNode ifs) {
        if (ifs == null || !ifs.isArray()) return null;
        for (var it : ifs) {
            var addrs = it.path("ip-addresses");
            if (!addrs.isArray()) continue;
            for (var a : addrs) {
                if (!"ipv4".equalsIgnoreCase(a.path("ip-address-type").asText())) continue;
                var ip = a.path("ip-address").asText("");
                if (ip.isBlank()) continue;
                if (ip.startsWith("127.")) continue;
                return ip;
            }
        }
        return null;
    }

    private record VmInfo(String ip, String status) {}
}

