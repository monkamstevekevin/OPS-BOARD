package org.caureq.caureqopsboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LiveStatusService {
    private final AssetRepo assetRepo;
    private final ProxmoxClient proxmox;
    private final org.caureq.caureqopsboard.config.AppProps props;
    private final ActionService actionService;

    @Value("${app.status.refresh-ms:30000}")
    private long refreshMs;
    @Value("${app.status.touch-last-seen:false}")
    private boolean touchLastSeen;

    public record LiveStatus(String hostname, String node, Integer vmid,
                             String vmState, Boolean qgaUp, String ipv4,
                             Integer topPid, String topName, Double topCpu, Double topMem,
                             Instant fetchedAt) {}

    private final Map<String, LiveStatus> cache = new ConcurrentHashMap<>();

    public List<LiveStatus> all() { return new ArrayList<>(cache.values()); }
    public Optional<LiveStatus> get(String hostname) { return Optional.ofNullable(cache.get(hostname)); }

    @Scheduled(fixedDelayString = "${app.status.refresh-ms:30000}")
    public void refresh() {
        var assets = assetRepo.findAll();
        for (Asset a : assets) {
            try {
                var meta = resolveVm(a);
                if (meta.vmid == null || meta.node == null || meta.node.isBlank()) {
                    // Not a Proxmox VM; optionally capture local top if the asset is local
                    try {
                        if (local.isLocalAsset(a) && captureTop && shouldCaptureTop(a.getHostname())) {
                            var m = local.diagnose(1);
                            Integer topPid = null; String topName = null; Double topCpu = null; Double topMem = null;
                            Object cpuTop = m.get("cpuTop");
                            if (cpuTop instanceof java.util.List<?> list && !list.isEmpty()) {
                                var first = (java.util.Map<?,?>) list.get(0);
                                topPid = toInt(first.get("pid")); topName = toStr(first.get("name")); topCpu = toDbl(first.get("cpu")); topMem = toDbl(first.get("mem"));
                            }
                            cache.put(a.getHostname(), new LiveStatus(a.getHostname(), null, null, null, null, null, topPid, topName, topCpu, topMem, Instant.now()));
                        }
                    } catch (Exception ignored) {}
                    continue;
                }
                String state;
                try {
                    var cur = proxmox.vmCurrentStatus(meta.node, meta.vmid);
                    state = cur.path("status").asText("");
                } catch (ProxmoxApiException ex) {
                    // If VM is missing in Proxmox, tag as 'missing' so UI can highlight
                    if (ex.status() == 404) {
                        cache.put(a.getHostname(), new LiveStatus(a.getHostname(), meta.node, meta.vmid,
                                "missing", null, null, null, null, null, null, Instant.now()));
                        continue;
                    }
                    throw ex;
                }
                Boolean qga = null;
                try {
                    proxmox.agentPing(meta.node, meta.vmid);
                    qga = true;
                } catch (Exception e) {
                    qga = false;
                }
                String ip = null;
                try {
                    JsonNode ifs = proxmox.agentNetworkGetInterfaces(meta.node, meta.vmid);
                    ip = pickBestIpv4(ifs);
                } catch (Exception ignored) {}

                Integer topPid = null; String topName = null; Double topCpu = null; Double topMem = null;
                if (qga != null && qga && "running".equalsIgnoreCase(state)) {
                    if (shouldCaptureTop(a.getHostname())) {
                        try {
                            var diag = diagnoseTop(a);
                            if (diag != null) {
                                topPid = diag.pid; topName = diag.name; topCpu = diag.cpu; topMem = diag.mem;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                cache.put(a.getHostname(), new LiveStatus(
                        a.getHostname(), meta.node, meta.vmid,
                        state, qga, ip,
                        topPid, topName, topCpu, topMem,
                        Instant.now()
                ));

                // Option: mettre à jour lastSeen quand la VM est running (pour refléter une présence)
                if (touchLastSeen && "running".equalsIgnoreCase(state)) {
                    var now = Instant.now();
                    var ls = a.getLastSeen();
                    // évite les writes trop fréquents si déjà récent (< refreshMs/2)
                    if (ls == null || now.minusMillis(Math.max(5000, refreshMs / 2)).isAfter(ls)) {
                        a.setLastSeen(now);
                        try { assetRepo.save(a); } catch (Exception e) { log.debug("lastSeen save failed for {}: {}", a.getHostname(), e.getMessage()); }
                    }
                }
            } catch (Exception e) {
                log.debug("live refresh failed for {} -> {}", a.getHostname(), e.getMessage());
            }
        }
    }

    private String pickBestIpv4(JsonNode ifs) {
        if (ifs == null || !ifs.isArray()) return null;
        List<String> ips = new ArrayList<>();
        for (var it : ifs) {
            var addrs = it.path("ip-addresses");
            if (!addrs.isArray()) continue;
            for (var a : addrs) {
                if (!"ipv4".equalsIgnoreCase(a.path("ip-address-type").asText())) continue;
                var ip = a.path("ip-address").asText("");
                if (ip.isBlank()) continue;
                if (ip.startsWith("127.")) continue;
                ips.add(ip);
            }
        }
        if (ips.isEmpty()) return null;
        for (var s : ips) {
            if (s.startsWith("10.") || s.startsWith("192.168.") || s.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return s;
        }
        return ips.get(0);
    }

    private VmMeta resolveVm(Asset a) {
        Integer vmid = a.getVmid();
        String node = a.getNode();
        if (vmid == null && a.getHostname()!=null) {
            var m = Pattern.compile("^vm-(\\d+)-").matcher(a.getHostname());
            if (m.find()) vmid = Integer.parseInt(m.group(1));
        }
        if (node == null || node.isBlank()) node = (props.defaultNode()==null || props.defaultNode().isBlank()) ? "Caureqlab" : props.defaultNode();
        return new VmMeta(node, vmid);
    }

    private record VmMeta(String node, Integer vmid) {}
    private final LocalDiagnosticService local;
    public LiveStatusService(AssetRepo assetRepo, ProxmoxClient proxmox,
                             org.caureq.caureqopsboard.config.AppProps props,
                             LocalDiagnosticService local,
                             ActionService actionService) {
        this.assetRepo = assetRepo;
        this.proxmox = proxmox;
        this.props = props;
        this.local = local;
        this.actionService = actionService;
    }
    private Integer toInt(Object o){ try { return o==null?null: Integer.parseInt(String.valueOf(o)); } catch(Exception e){ return null; } }
    private String toStr(Object o){ return o==null?null:String.valueOf(o); }
    private Double toDbl(Object o){ try { return o==null?null: Double.parseDouble(String.valueOf(o)); } catch(Exception e){ return null; } }

    @Value("${app.status.capture-top:false}")
    private boolean captureTop;
    @Value("${app.status.capture-interval-ms:120000}")
    private long captureIntervalMs;
    private final Map<String, Long> lastTopCapture = new ConcurrentHashMap<>();
    private boolean shouldCaptureTop(String host) {
        if (!captureTop) return false;
        long now = System.currentTimeMillis();
        Long last = lastTopCapture.get(host);
        if (last == null || now - last > Math.max(30000, captureIntervalMs)) { lastTopCapture.put(host, now); return true; }
        return false;
    }
    private static class TopDiag { Integer pid; String name; Double cpu; Double mem; }
    private TopDiag diagnoseTop(Asset a) {
        // Reuse DiagnosticService via QGA by calling ps to get top one
        try {
            // prefer CPU top
            String node = resolveVm(a).node;
            int vmid = resolveVm(a).vmid;
            var r = actionService.execOnVm(node, vmid, "/bin/sh", List.of("-lc", "ps -eo pid,comm,%cpu,%mem --no-headers --sort=-%cpu | head -n 1"), null, java.time.Duration.ofSeconds(6));
            if (r.exitCode() == 0 && r.stdout()!=null && !r.stdout().isBlank()) {
                var parts = r.stdout().trim().split("\\s+", 4);
                if (parts.length>=4) {
                    var td = new TopDiag();
                    td.pid = Integer.parseInt(parts[0]); td.name = parts[1];
                    double raw = Double.parseDouble(parts[2]);
                    // get logical cpu count
                    int nproc = 1;
                    try {
                        var np = actionService.execOnVm(node, vmid, "/bin/sh", List.of("-lc", "nproc 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1"), null, java.time.Duration.ofSeconds(4));
                        nproc = Integer.parseInt(np.stdout().trim());
                        if (nproc <= 0) nproc = 1;
                    } catch (Exception ignored) {}
                    td.cpu = Math.round((raw / nproc) * 10.0) / 10.0;
                    td.mem = Double.parseDouble(parts[3]);
                    return td;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
