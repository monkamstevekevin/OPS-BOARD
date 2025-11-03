package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.AppProps;
import org.caureq.caureqopsboard.domain.Asset;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagnosticService {
    private final ActionService actionService;
    private final AppProps props;
    private final LocalDiagnosticService localDiag;

    /** Returns a concise one-line summary of the top process by CPU or RAM inside the VM if possible. */
    public String captureTopProcess(Asset asset) {
        try {
            VmMeta vm = resolveVm(asset);
            if (vm == null) {
                if (localDiag.isLocalAsset(asset)) {
                    var m = localDiag.diagnose(1);
                    Object cpuTop = m.get("cpuTop");
                    if (cpuTop instanceof java.util.List<?> list && !list.isEmpty()) {
                        var first = (java.util.Map<?,?>) list.get(0);
                        return ("LOCAL PID="+first.get("pid")+" NAME="+first.get("name")+" CPU="+first.get("cpu")+" MEM="+first.get("mem"));
                    }
                }
                return null; // not a Proxmox VM and not local
            }
            // Try Windows PowerShell first (percent CPU via PerfFormattedData)
            var psCmd = List.of(
                    "powershell","-NoProfile","-Command",
                    "Try { $cores=(Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors; if(-not $cores){$cores=1}; $p=Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | Where-Object { $_.IDProcess -ne 0 } | Sort-Object PercentProcessorTime -desc | Select-Object -First 1; if($p){ $norm=[math]::Round(($p.PercentProcessorTime/[double]$cores),1); ('WIN PID='+$p.IDProcess+' NAME='+$p.Name+' CPU='+$norm+' WS='+$p.WorkingSet) } } Catch { }"
            );
            var r1 = actionService.execOnVm(vm.node(), vm.vmid(), psCmd.get(0), psCmd.subList(1, psCmd.size()), null, Duration.ofSeconds(8));
            if (r1.exitCode() == 0 && r1.stdout()!=null && !r1.stdout().isBlank()) {
                return r1.stdout().trim();
            }
            // Fallback Linux shell
            var shCmd = List.of("/bin/sh","-lc",
                    "ps -eo pid,comm,%cpu,%mem --sort=-%cpu | head -n 2 | tail -n 1 | awk '{print \"LIN PID=\" $1 \" NAME=\" $2 \" CPU=\" $3 \" MEM=\" $4}'"
            );
            var r2 = actionService.execOnVm(vm.node(), vm.vmid(), shCmd.get(0), shCmd.subList(1, shCmd.size()), null, Duration.ofSeconds(8));
            if (r2.exitCode() == 0 && r2.stdout()!=null && !r2.stdout().isBlank()) {
                return r2.stdout().trim();
            }
        } catch (Exception e) {
            log.debug("diag capture failed for {}: {}", asset.getHostname(), e.getMessage());
        }
        return null;
    }

    /** Returns structured diagnostics: top N by CPU and MEM if possible. */
    public java.util.Map<String,Object> diagnose(Asset asset, int topN) {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        try {
            VmMeta vm = resolveVm(asset);
            if (vm == null) {
                if (localDiag.isLocalAsset(asset)) {
                    return localDiag.diagnose(topN);
                }
                out.put("notVm", true);
                out.put("message", "Host is not mapped to a Proxmox VM (node/vmid missing)");
                return out;
            }
            // Windows JSON using PerfFormattedData (CPU percent)
            var ps = java.util.List.of(
                    "powershell","-NoProfile","-Command",
                    ("$n=%d; $cores=(Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors; if(-not $cores){$cores=1}; $perf=Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | Where-Object { $_.IDProcess -ne 0 }; " +
                            "$cpu=$perf | Sort-Object PercentProcessorTime -desc | Select-Object -First $n | Select-Object IDProcess,Name,@{Name='PercentProcessorTime';Expression={[math]::Round(($_.PercentProcessorTime/[double]$cores),1)}},WorkingSet; " +
                            "$mem=$perf | Sort-Object WorkingSet -desc | Select-Object -First $n | Select-Object IDProcess,Name,@{Name='PercentProcessorTime';Expression={[math]::Round(($_.PercentProcessorTime/[double]$cores),1)}},WorkingSet; " +
                            "[pscustomobject]@{ os='WIN'; cpuTop=$cpu; memTop=$mem } | ConvertTo-Json -Compress").formatted(topN)
            );
            var r1 = actionService.execOnVm(vm.node(), vm.vmid(), ps.get(0), ps.subList(1, ps.size()), null, java.time.Duration.ofSeconds(10));
            if (r1.exitCode() == 0 && r1.stdout()!=null && r1.stdout().trim().startsWith("{")) {
                out = new com.fasterxml.jackson.databind.ObjectMapper().readValue(r1.stdout(), java.util.Map.class);
                return out;
            }
            // Linux: parse ps output
            String cmdCpu = "ps -eo pid,comm,%cpu,%mem --no-headers --sort=-%cpu | head -n " + topN;
            String cmdMem = "ps -eo pid,comm,%cpu,%mem --no-headers --sort=-%mem | head -n " + topN;
            var rCpu = actionService.execOnVm(vm.node(), vm.vmid(), "/bin/sh", java.util.List.of("-lc", cmdCpu), null, java.time.Duration.ofSeconds(8));
            var rMem = actionService.execOnVm(vm.node(), vm.vmid(), "/bin/sh", java.util.List.of("-lc", cmdMem), null, java.time.Duration.ofSeconds(8));
            java.util.function.Function<String, java.util.List<java.util.Map<String,Object>>> parse = (s) -> {
                java.util.List<java.util.Map<String,Object>> list = new java.util.ArrayList<>();
                if (s == null) return list;
                for (var line : s.split("\n")) {
                    line = line.trim(); if (line.isBlank()) continue;
                    var parts = line.split("\\s+", 4);
                    if (parts.length >= 4) {
                        try {
                            java.util.Map<String,Object> m = new java.util.HashMap<>();
                            m.put("pid", Integer.parseInt(parts[0]));
                            m.put("name", parts[1]);
                            m.put("cpu", Double.parseDouble(parts[2]));
                            m.put("mem", Double.parseDouble(parts[3]));
                            list.add(m);
                        } catch (Exception ignored) {}
                    }
                }
                return list;
            };
            out.put("os", "LIN");
            out.put("cpuTop", parse.apply(rCpu.stdout()));
            out.put("memTop", parse.apply(rMem.stdout()));
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    private VmMeta resolveVm(Asset a) {
        Integer vmid = a.getVmid();
        String node = a.getNode();
        if (vmid == null && a.getHostname()!=null) {
            var m = Pattern.compile("^vm-(\\d+)-", Pattern.CASE_INSENSITIVE).matcher(a.getHostname());
            if (m.find()) vmid = Integer.parseInt(m.group(1));
        }
        if (node == null || node.isBlank()) {
            node = (props.defaultNode()==null || props.defaultNode().isBlank()) ? "Caureqlab" : props.defaultNode();
        }
        if (vmid == null) return null; // not a Proxmox VM
        return new VmMeta(node, vmid);
    }

    private record VmMeta(String node, int vmid) {}
}
