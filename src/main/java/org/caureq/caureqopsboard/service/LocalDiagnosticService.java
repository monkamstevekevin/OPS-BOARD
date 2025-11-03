package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.caureq.caureqopsboard.config.AppProps;
import org.caureq.caureqopsboard.domain.Asset;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalDiagnosticService {
    private final AppProps props;

    public boolean isLocalAsset(Asset a) {
        if (a == null || a.getHostname() == null) return false;
        String hn = a.getHostname().trim();
        // Compare against configured list (comma separated)
        if (props.localHosts() != null && !props.localHosts().isBlank()) {
            for (String s : props.localHosts().split(",")) {
                if (hn.equalsIgnoreCase(s.trim())) return true;
            }
        }
        // Compare against OS env/hostnames
        try {
            String env1 = System.getenv("COMPUTERNAME");
            String env2 = System.getenv("HOSTNAME");
            String jhn = java.net.InetAddress.getLocalHost().getHostName();
            if (env1 != null && hn.equalsIgnoreCase(env1)) return true;
            if (env2 != null && hn.equalsIgnoreCase(env2)) return true;
            if (jhn != null && hn.equalsIgnoreCase(jhn)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public Map<String, Object> diagnose(int topN) {
        Map<String,Object> out = new HashMap<>();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        out.put("os", os);
        try {
            if (os.contains("win")) {
                String script = (
                        "$n=%d; $cores=(Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors; if(-not $cores){$cores=1}; $perf=Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | Where-Object { $_.IDProcess -ne 0 }; " +
                        "$cpu=$perf | Sort-Object PercentProcessorTime -desc | Select-Object -First $n | Select-Object IDProcess,Name,@{Name='PercentProcessorTime';Expression={[math]::Round(($_.PercentProcessorTime/[double]$cores),1)}},WorkingSet; " +
                        "$mem=$perf | Sort-Object WorkingSet -desc | Select-Object -First $n | Select-Object IDProcess,Name,@{Name='PercentProcessorTime';Expression={[math]::Round(($_.PercentProcessorTime/[double]$cores),1)}},WorkingSet; " +
                        "[pscustomobject]@{ os='WIN-LOCAL'; cpuTop=$cpu; memTop=$mem } | ConvertTo-Json -Compress").formatted(topN);
                String json = run(new String[]{"powershell","-NoProfile","-Command", script}, 10000);
                if (json != null && json.trim().startsWith("{")) {
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
                }
            } else {
                String cmdCpu = "ps -eo pid,comm,%cpu,%mem --no-headers --sort=-%cpu | head -n " + topN;
                String cmdMem = "ps -eo pid,comm,%cpu,%mem --no-headers --sort=-%mem | head -n " + topN;
                String outCpu = run(new String[]{"/bin/sh","-lc", cmdCpu}, 8000);
                String outMem = run(new String[]{"/bin/sh","-lc", cmdMem}, 8000);
                out.put("os", "LIN-LOCAL");
                out.put("cpuTop", parsePs(outCpu));
                out.put("memTop", parsePs(outMem));
            }
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    private List<Map<String,Object>> parsePs(String s) {
        List<Map<String,Object>> list = new ArrayList<>();
        if (s == null) return list;
        for (String line : s.split("\n")) {
            line = line.trim(); if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 4);
            if (parts.length >= 4) {
                try {
                    Map<String,Object> m = new HashMap<>();
                    m.put("pid", Integer.parseInt(parts[0]));
                    m.put("name", parts[1]);
                    m.put("cpu", Double.parseDouble(parts[2]));
                    m.put("mem", Double.parseDouble(parts[3]));
                    list.add(m);
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private String run(String[] cmd, int timeoutMs) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        boolean ended = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!ended) p.destroyForcibly();
        return sb.toString();
    }
}
