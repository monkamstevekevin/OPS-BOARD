package org.caureq.caureqopsboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facade for inventory discovery from Proxmox.
 * Discovers VMs and proposes mapping (node/vmid) for existing assets.
 * Does not create or delete assets unless apply() is called with createMissing=true.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryDiscoveryService {
    private final ProxmoxClient proxmox;
    private final AssetRepo assetRepo;

    public record Proposal(String hostname, String node, Integer vmid, String reason) {}
    public record PreviewResult(List<Proposal> toUpdate,
                                List<Map<String,Object>> unknownVms,
                                List<Map<String,Object>> missingAssets) {}

    /**
     * List VMs from Proxmox and propose asset updates where node/vmid are missing or different.
     */
    public PreviewResult preview() {
        List<Proposal> proposals = new ArrayList<>();
        List<Map<String,Object>> unknown = new ArrayList<>();
        List<Map<String,Object>> missing = new ArrayList<>();
        Map<Integer,String> vmNameById = new HashMap<>();
        // Track presence as both (node,vmid) and by vmid alone (for assets missing node)
        java.util.Set<String> presentPairs = new java.util.HashSet<>(); // node#vmid
        java.util.Set<Integer> presentVmids = new java.util.HashSet<>();

        boolean usedClusterEndpoint = false;
        try {
            // Prefer cluster-wide listing (covers qemu + lxc)
            var vms = proxmox.simpleGetJson(proxmox.props().baseUrl() + "/cluster/resources?type=vm");
            for (var it : vms.path("data")) {
                usedClusterEndpoint = true;
                String type = it.path("type").asText(""); // qemu or lxc
                String node = it.path("node").asText("");
                int vmid = it.path("vmid").asInt(-1);
                String name = it.path("name").asText("");
                if (vmid < 0 || node.isBlank()) continue;
                vmNameById.put(vmid, name);
                presentVmids.add(vmid);
                presentPairs.add(node.toLowerCase() + "#" + vmid);
                String expectedHostname = "vm-" + vmid + (name.isBlank()? "" : ("-" + name));
                Optional<Asset> match = findAssetByVmidOrName(vmid, name, expectedHostname);
                if (match.isPresent()) {
                    var a = match.get();
                    boolean needs = (a.getVmid() == null || !Objects.equals(a.getVmid(), vmid))
                            || (a.getNode() == null || !a.getNode().equalsIgnoreCase(node));
                    if (needs) {
                        proposals.add(new Proposal(a.getHostname(), node, vmid, "match:" + matchReason(a, vmid, name)));
                    }
                } else {
                    unknown.add(Map.of("node", node, "vmid", vmid, "name", name, "type", type));
                }
            }
        } catch (Exception ignored) { usedClusterEndpoint = false; }

        if (!usedClusterEndpoint || presentVmids.isEmpty()) {
            // Fallback per-node (handles RBAC setups without cluster scope)
            try {
                var nodes = proxmox.simpleGetJson(proxmox.props().baseUrl() + "/nodes");
                for (var n : nodes.path("data")) {
                    String node = n.path("node").asText("");
                    if (node.isBlank()) continue;
                    for (String kind : List.of("qemu", "lxc")) {
                        try {
                            var list = proxmox.simpleGetJson(proxmox.props().baseUrl() + "/nodes/" + node + "/" + kind);
                            for (var vm : list.path("data")) {
                                int vmid = vm.path("vmid").asInt(-1);
                                String name = vm.path("name").asText("");
                                if (vmid < 0) continue;
                                vmNameById.put(vmid, name);
                                presentVmids.add(vmid);
                                presentPairs.add(node.toLowerCase() + "#" + vmid);
                                String expectedHostname = "vm-" + vmid + (name.isBlank()? "" : ("-" + name));
                                Optional<Asset> match = findAssetByVmidOrName(vmid, name, expectedHostname);
                                if (match.isPresent()) {
                                    var a = match.get();
                                    boolean needs = (a.getVmid() == null || !Objects.equals(a.getVmid(), vmid))
                                            || (a.getNode() == null || !a.getNode().equalsIgnoreCase(node));
                                    if (needs) {
                                        proposals.add(new Proposal(a.getHostname(), node, vmid, "match:" + matchReason(a, vmid, name)));
                                    }
                                } else {
                                    unknown.add(Map.of("node", node, "vmid", vmid, "name", name, "type", kind));
                                }
                            }
                        } catch (Exception ignored2) { /* skip node-kind errors */ }
                    }
                }
            } catch (Exception ignored3) {}
        }

        // Assets mapped to VMIDs that are no longer present in Proxmox
        for (var a : assetRepo.findAll()) {
            if (a.getVmid() == null) continue;
            String aNode = (a.getNode()==null?"":a.getNode().toLowerCase());
            boolean exists;
            if (aNode.isBlank()) {
                // When node is unknown, try by vmid and name consistency
                exists = presentVmids.contains(a.getVmid());
                String name = vmNameById.get(a.getVmid());
                if (exists && name != null) {
                    // If vmid is reused for a different name, consider missing for this asset
                    String expected = a.getHostname();
                    if (!(expected != null && (expected.equalsIgnoreCase(name) || expected.equalsIgnoreCase("vm-"+a.getVmid()+"-"+name)))) {
                        exists = false;
                    }
                }
            } else {
                exists = presentPairs.contains(aNode + "#" + a.getVmid());
            }
            if (!exists) {
                // Final confirmation via direct status/current to avoid RBAC list gaps
                try {
                    if (!aNode.isBlank()) {
                        proxmox.vmCurrentStatus(aNode, a.getVmid());
                        continue; // reachable -> not missing
                    }
                } catch (ProxmoxApiException ex) {
                    if (ex.status() == 404) {
                        missing.add(Map.of("hostname", a.getHostname(), "node", a.getNode(), "vmid", a.getVmid()));
                        continue;
                    }
                } catch (Exception ignore) {}
                // If still not confirmed present, mark as missing
                missing.add(Map.of("hostname", a.getHostname(), "node", a.getNode(), "vmid", a.getVmid()));
            }
        }

        return new PreviewResult(proposals, unknown, missing);
    }

    /** Apply proposals to DB. */
    public int apply(List<Proposal> list) {
        int updated = 0;
        for (var p : list) {
            var opt = assetRepo.findByHostnameIgnoreCase(p.hostname());
            if (opt.isPresent()) {
                var a = opt.get();
                a.setNode(p.node());
                a.setVmid(p.vmid());
                assetRepo.save(a);
                updated++;
            }
        }
        return updated;
    }

    /** Clear node/vmid mapping for given hostnames. */
    public int clearMappings(List<String> hostnames) {
        if (hostnames == null || hostnames.isEmpty()) return 0;
        int updated = 0;
        for (var h : hostnames) {
            var opt = assetRepo.findByHostnameIgnoreCase(h);
            if (opt.isPresent()) {
                var a = opt.get();
                if (a.getNode()!=null || a.getVmid()!=null) {
                    a.setNode(null);
                    a.setVmid(null);
                    assetRepo.save(a);
                    updated++;
                }
            }
        }
        return updated;
    }

    /** Archive (retire) missing assets by adding 'retired' tag and clearing mapping. */
    public int archiveMissing(List<String> hostnames) {
        if (hostnames == null || hostnames.isEmpty()) return 0;
        int updated = 0;
        for (var h : hostnames) {
            var opt = assetRepo.findByHostnameIgnoreCase(h);
            if (opt.isPresent()) {
                var a = opt.get();
                // clear mapping
                a.setNode(null); a.setVmid(null);
                // add 'retired' tag if not present
                String tags = a.getTags();
                boolean has = false;
                if (tags != null && !tags.isBlank()) {
                    for (var t : tags.split(",")) if ("retired".equalsIgnoreCase(t.trim())) { has = true; break; }
                }
                if (!has) {
                    a.setTags((tags==null||tags.isBlank()) ? "retired" : (tags + ",retired"));
                }
                assetRepo.save(a);
                updated++;
            }
        }
        return updated;
    }

    private Optional<Asset> findAssetByVmidOrName(int vmid, String name, String expectedHostname) {
        // 1) direct hostname match
        var direct = assetRepo.findByHostnameIgnoreCase(expectedHostname);
        if (direct.isPresent()) return direct;
        // 2) match by vmid extracted from hostname
        Pattern pat = Pattern.compile("^vm-(\\d+)-", Pattern.CASE_INSENSITIVE);
        for (var a : assetRepo.findAll()) {
            if (a.getHostname() == null) continue;
            Matcher m = pat.matcher(a.getHostname());
            if (m.find() && Integer.toString(vmid).equals(m.group(1))) return Optional.of(a);
            if (!name.isBlank() && a.getHostname().equalsIgnoreCase(name)) return Optional.of(a);
        }
        return Optional.empty();
    }

    private String matchReason(Asset a, int vmid, String name) {
        if (a.getHostname() != null && a.getHostname().equalsIgnoreCase("vm-"+vmid+(name.isBlank()?"":"-"+name))) return "hostname=vm-<vmid>-name";
        if (a.getHostname() != null && a.getHostname().equalsIgnoreCase(name)) return "hostname=name";
        return "vmid-regex";
    }
}
