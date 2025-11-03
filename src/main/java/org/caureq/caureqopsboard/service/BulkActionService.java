package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BulkActionService {
    private final ProxmoxClient proxmox;
    private final AssetRepo assetRepo;
    private final AuditService audit;

    public record Request(List<String> hostnames, String tag) {}
    public record Result(String hostname, String node, Integer vmid, String outcome, String upidOrError) {}

    public List<Result> start(Request req, jakarta.servlet.http.HttpServletRequest http) { return doAction("start", req, http); }
    public List<Result> shutdown(Request req, jakarta.servlet.http.HttpServletRequest http) { return doAction("shutdown", req, http); }
    public List<Result> stop(Request req, jakarta.servlet.http.HttpServletRequest http) { return doAction("stop", req, http); }
    public List<Result> reset(Request req, jakarta.servlet.http.HttpServletRequest http) { return doAction("reset", req, http); }

    private List<Result> doAction(String action, Request req, jakarta.servlet.http.HttpServletRequest http) {
        var targets = resolveTargets(req);
        List<Result> out = new ArrayList<>();
        for (var a : targets) {
            var vm = findVm(a);
            try {
                String upid;
                switch (action) {
                    case "start" -> upid = proxmox.startVm(vm.node(), vm.vmid());
                    case "shutdown" -> upid = proxmox.shutdownVm(vm.node(), vm.vmid());
                    case "stop" -> upid = proxmox.stopVm(vm.node(), vm.vmid());
                    case "reset" -> upid = proxmox.resetVm(vm.node(), vm.vmid());
                    default -> throw new IllegalArgumentException("unknown action: " + action);
                }
                audit.log(http, "admin-api", action, vm.node(), vm.vmid(), "UPID=" + upid + ";host=" + a.getHostname());
                out.add(new Result(a.getHostname(), vm.node(), vm.vmid(), "OK", upid));
            } catch (Exception e) {
                out.add(new Result(a.getHostname(), vm.node(), vm.vmid(), "ERROR", e.getMessage()));
            }
        }
        return out;
    }

    private List<Asset> resolveTargets(Request req) {
        Set<String> names = new LinkedHashSet<>();
        if (req.hostnames() != null) {
            req.hostnames().forEach(h -> { if (h != null && !h.isBlank()) names.add(h.trim()); });
        }
        List<Asset> all = assetRepo.findAll();
        List<Asset> list = new ArrayList<>();
        if (req.tag() != null && !req.tag().isBlank()) {
            var tag = req.tag().trim().toLowerCase(Locale.ROOT);
            for (var a : all) {
                if (a.getTags() == null) continue;
                var tags = Arrays.stream(a.getTags().split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
                if (tags.contains(tag)) list.add(a);
            }
        }
        if (!names.isEmpty()) {
            for (var n : names) {
                assetRepo.findByHostnameIgnoreCase(n).ifPresent(list::add);
            }
        }
        // de-duplicate by hostname keeping order
        Map<String, Asset> uniq = new LinkedHashMap<>();
        for (var a : list) uniq.put(a.getHostname(), a);
        return new ArrayList<>(uniq.values());
    }

    private VmMeta findVm(Asset a) {
        Integer vmid = a.getVmid();
        String node = a.getNode();
        if (vmid == null) {
            var m = Pattern.compile("^vm-(\\d+)-", Pattern.CASE_INSENSITIVE).matcher(a.getHostname()==null?"":a.getHostname());
            if (m.find()) vmid = Integer.parseInt(m.group(1));
        }
        if (node == null || node.isBlank()) node = "Caureqlab"; // fallback; discovery should fill real node
        if (vmid == null) throw new IllegalArgumentException("cannot resolve vmid for host=" + a.getHostname());
        return new VmMeta(node, vmid);
    }

    private record VmMeta(String node, int vmid) {}
}
